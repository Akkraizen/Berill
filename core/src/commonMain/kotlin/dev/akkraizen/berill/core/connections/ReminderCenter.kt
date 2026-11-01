package dev.akkraizen.berill.core.connections

import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.plugin.PluginHost
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import kotlin.time.Clock
import dev.akkraizen.berill.core.notifications.*
import dev.akkraizen.berill.core.preferences.LocalPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

interface ReminderScheduler {
  suspend fun requestPermission(): Boolean
  fun replace(group: String, reminders: List<Reminder>)
  fun cancel(id: String)
  fun configure(preferences: NotificationPreferences, activePlugins: Set<String>) {}
}
object NoReminderScheduler : ReminderScheduler {
  override suspend fun requestPermission() = false
  override fun replace(group: String, reminders: List<Reminder>) {}
  override fun cancel(id: String) {}
}

class ReminderCenter(private val store: LocalStore, private val scheduler: ReminderScheduler,
  private val preferences: LocalPreferences<NotificationPreferences> = LocalPreferences(store, "notifications.preferences", NotificationPreferences.serializer(), NotificationPreferences()),
  private val onFailure: (Exception) -> Unit = { dev.akkraizen.berill.core.log.AppLog.error("reminders", "schedule", it) }
) : ReminderAccess {
  var onDaily: (List<Reminder>) -> Unit = {}
  var onTimer: (Reminder) -> Unit = {}
  var onCancel: (String) -> Unit = {}
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val timers = try { store.meta("reminders.timers")?.let { json.decodeFromString<Map<String, Reminder>>(it) }.orEmpty().toMutableMap() } catch (e: Exception) { mutableMapOf<String, Reminder>() }
  private val timerLock = SynchronizedObject()
  private val activePlugins = MutableStateFlow<Set<String>?>(null)
  private fun allowed(reminder: Reminder): Boolean = preferences.state.value.allows(reminder) && (reminder.owner()?.let { activePlugins.value?.contains(it) } != false)
  private fun saveTimers() = store.setMeta("reminders.timers", json.encodeToString(timers.toMap()))
  val enabled = MutableStateFlow(store.meta("reminders.enabled") == "1")
  suspend fun setEnabled(on: Boolean) {
    if (on && !scheduler.requestPermission()) throw FeatureException(FailureReason.ACCESS_DENIED)
    store.setMeta("reminders.enabled", if (on) "1" else "0")
    enabled.value = on
    if (!enabled.value) { scheduler.replace("daily", emptyList()); onDaily(emptyList()) }
  }
  override suspend fun schedule(reminder: Reminder) {
    try {
      if (allowed(reminder) && !scheduler.requestPermission()) throw FeatureException(FailureReason.ACCESS_DENIED)
      synchronized(timerLock) {
        timers[reminder.id] = reminder; saveTimers()
        scheduler.replace("timer:${reminder.id}", listOf(reminder).filter(::allowed))
        onTimer(reminder)
      }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { onFailure(e) }
  }
  override fun cancel(id: String) {
    try { synchronized(timerLock) { timers.remove(id); saveTimers(); scheduler.cancel(id); onCancel(id) } }
    catch (e: CancellationException) { throw e } catch (e: Exception) { onFailure(e) }
  }

  suspend fun refresh(plugins: PluginHost) {
    activePlugins.value = plugins.enabled.value.map { it.id }.toSet()
    scheduler.configure(preferences.state.value, activePlugins.value.orEmpty())
    val now = Clock.System.now().toEpochMilliseconds()
    synchronized(timerLock) {
      val expired = timers.values.filter { it.atMillis <= now }.map { it.id }
      expired.forEach { timers.remove(it); scheduler.cancel(it) }
      if (expired.isNotEmpty()) saveTimers()
      timers.values.toList().forEach { value -> scheduler.replace("timer:${value.id}", listOf(value).filter(::allowed)); onTimer(value) }
    }
    if (!enabled.value) { scheduler.replace("daily", emptyList()); onDaily(emptyList()); return }
    val reminders = mutableListOf<Reminder>()
    for (plugin in plugins.enabled.value) {
      try {
        val sources = plugin.provides(plugins.context(plugin))
        sources.filterIsInstance<ReminderSource>().forEach { source -> reminders += source.reminders().map { it.copy(pluginId = plugin.id) } }
        sources.filterIsInstance<DeadlineSource>().forEach { source ->
          val deadlines = withTimeoutOrNull(5000) { source.deadlines().first() }.orEmpty()
          deadlines.forEach { deadline ->
            val at = LocalDateTime(deadline.date, LocalTime(9, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            if (at > Clock.System.now().toEpochMilliseconds()) reminders += Reminder("deadline:${deadline.pluginId}:${deadline.route}:${deadline.date}", plugin.title, deadline.title, at, WidgetAction.Open(pluginId = deadline.pluginId, route = deadline.route), pluginId = plugin.id, event = ReminderEvent.DEADLINE)
          }
        }
      } catch (e: CancellationException) { throw e } catch (e: Exception) { dev.akkraizen.berill.core.log.AppLog.error("reminders", plugin.id, e) }
    }
    val unique = reminders.distinctBy { it.id }
    scheduler.replace("daily", unique.filter(::allowed))
    onDaily(unique)
  }

  fun start(plugins: PluginHost, scope: CoroutineScope) {
    scope.launch {
      combine(preferences.state, plugins.enabled) { policy, active -> policy to active.map { it.id }.toSet() }.collect { (policy, active) ->
        activePlugins.value = active
        try { scheduler.configure(policy, active) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { onFailure(e) }
      }
    }
    scope.launch {
      merge(store.changes, preferences.state.map { Unit }, enabled.map { Unit }, plugins.enabled.map { Unit }, flow { while (true) { emit(Unit); delay(60000) } })
        .collect {
          try { refresh(plugins) }
          catch (e: CancellationException) { throw e }
          catch (e: Exception) { dev.akkraizen.berill.core.log.AppLog.error("reminders", "refresh", e) }
        }
    }
  }
}
