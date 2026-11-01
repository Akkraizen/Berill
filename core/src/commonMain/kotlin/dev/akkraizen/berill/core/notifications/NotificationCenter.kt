package dev.akkraizen.berill.core.notifications

import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.connections.*
import dev.akkraizen.berill.core.sync.SyncState
import dev.akkraizen.berill.api.Reminder
import dev.akkraizen.berill.api.widgets.WidgetAction
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Serializable enum class NoticeKind { OPERATION, ERROR, SYNC, CONFLICT, UPDATE, REMINDER }
@Serializable enum class NoticeStatus { CANCELLED, RUNNING, SUCCESS, ERROR, INFO }
@Serializable
data class Notice(val id: String, val kind: NoticeKind, val status: NoticeStatus = NoticeStatus.INFO, val at: Long = 0, val read: Boolean = false,
  val title: String? = null, val detail: String? = null, val operation: FailureOperation? = null, val reason: FailureReason? = null, val open: WidgetAction? = null, val pluginId: String? = null, val reminderEvent: dev.akkraizen.berill.api.ReminderEvent = dev.akkraizen.berill.api.ReminderEvent.REMINDER)

class NotificationCenter(private val store: LocalStore, private val preferences: () -> NotificationPreferences = { NotificationPreferences() }) {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val lock = SynchronizedObject()
  private val mutable = MutableStateFlow(try {
    val saved = store.meta(KEY)?.let { json.decodeFromString(ListSerializer(Notice.serializer()), it) }.orEmpty()
    saved.filterNot { it.kind == NoticeKind.SYNC }.also { filtered ->
      if (filtered.size != saved.size) try { store.setMeta(KEY, json.encodeToString(ListSerializer(Notice.serializer()), filtered)) }
      catch (e: Exception) { dev.akkraizen.berill.core.log.AppLog.error("notifications", "remove legacy sync notice", e) }
    }
  } catch (e: Exception) { emptyList() })
  val state = mutable.asStateFlow()
  val open = MutableStateFlow(false)
  val conflictsOpen = MutableStateFlow(false)
  private fun change(block: (List<Notice>) -> List<Notice>) = synchronized(lock) {
    val all = block(mutable.value).filterNot { it.kind == NoticeKind.SYNC }.sortedByDescending { it.at }
    val pinned = all.filter { it.kind == NoticeKind.CONFLICT || it.status == NoticeStatus.RUNNING || (!it.read && it.status == NoticeStatus.ERROR) }
    val next = (pinned + all.filterNot { it in pinned }.take(500)).sortedByDescending { it.at }
    try { store.setMeta(KEY, json.encodeToString(ListSerializer(Notice.serializer()), next)) }
    catch (e: Exception) { dev.akkraizen.berill.core.log.AppLog.error("notifications", "persist", e) }
    mutable.value = next
  }
  fun publish(notice: Notice, forceUnread: Boolean = false) = change { current ->
    val old = current.firstOrNull { it.id == notice.id }
    val next = if (!forceUnread && old != null && old.copy(at = notice.at, read = notice.read) == notice) notice.copy(read = old.read) else notice
    current.filterNot { it.id == notice.id } + next
  }
  fun isVisible(notice: Notice, activePlugins: Set<String>? = null): Boolean {
    val owner = notice.pluginId ?: (notice.open as? WidgetAction.Open)?.pluginId
    return notice.kind != NoticeKind.SYNC && preferences().allows(notice.kind, owner, notice.reminderEvent) && (notice.kind != NoticeKind.REMINDER || owner?.let { activePlugins?.contains(it) } != false)
  }
  fun markRead(id: String) = change { it.map { n -> if (n.id == id) n.copy(read = true) else n } }
  fun readAll() = change { it.map { n -> n.copy(read = true) } }
  fun clearRead() = change { it.filterNot { n -> n.read && n.status != NoticeStatus.RUNNING && n.kind != NoticeKind.CONFLICT } }
  fun report(problem: FeatureProblem) = publish(Notice("operation:${problem.operation}", NoticeKind.ERROR, NoticeStatus.ERROR, now(), operation = problem.operation, reason = problem.reason), forceUnread = true)
  fun operation(operation: FailureOperation, progress: FeatureProgress) = publish(Notice("operation:$operation", NoticeKind.OPERATION,
    when (progress) { FeatureProgress.RUNNING -> NoticeStatus.RUNNING; FeatureProgress.SUCCESS -> NoticeStatus.SUCCESS; FeatureProgress.CANCELLED -> NoticeStatus.CANCELLED }, now(), operation = operation))
  // Keep the entry point harmless for already-running hot-reload collectors.
  @Deprecated("Sync status is shown outside notifications")
  fun sync(@Suppress("UNUSED_PARAMETER") state: SyncState) = Unit
  fun conflicts(values: List<dev.akkraizen.berill.core.sync.SyncConflict>) = change { old ->
    val ids = values.map { it.id }
    old.filterNot { it.kind == NoticeKind.CONFLICT && it.id.removePrefix("conflict:") !in ids } + values.filter { conflict -> old.none { it.id == "conflict:${conflict.id}" } }.map { conflict ->
      val label = listOf("title", "name", "text", "word").firstNotNullOfOrNull { key -> (conflict.local.fields[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() } }
      Notice("conflict:${conflict.id}", NoticeKind.CONFLICT, NoticeStatus.INFO, conflict.at, detail = "${conflict.local.collection} · ${label ?: conflict.local.id}")
    }
  }
  fun reminders(group: String, values: List<Reminder>) = change { old ->
    val prefix = "reminder:$group:"
    val ids = values.map { prefix + it.id }.toSet()
    old.filterNot { it.id.startsWith(prefix) && it.at > now() && it.id !in ids } .filterNot { it.id in ids } + values.map { value ->
      val previous = old.firstOrNull { it.id == prefix + value.id }
      Notice(prefix + value.id, NoticeKind.REMINDER, NoticeStatus.INFO, value.atMillis, read = previous?.read ?: false, title = value.title, detail = value.body, open = value.open, pluginId = value.owner(), reminderEvent = value.event)
    }
  }
  fun cancelReminder(id: String) = change { it.filterNot { n -> n.kind == NoticeKind.REMINDER && n.id.endsWith(":$id") } }
  private fun now() = Clock.System.now().toEpochMilliseconds()
  companion object { private const val KEY = "notifications.v1" }
}
