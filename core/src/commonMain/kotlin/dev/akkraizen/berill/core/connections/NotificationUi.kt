package dev.akkraizen.berill.core.connections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.notifications.*
import dev.akkraizen.berill.core.sync.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import dev.akkraizen.berill.api.ui.dayMonth

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NotificationDialog(core: BerillCore) {
  val notices by core.notifications.state.collectAsState()
  val preferences by core.notificationPreferences.state.collectAsState()
  val activePlugins by core.plugins.enabled.collectAsState()
  val conflicts by core.store.conflicts.state.collectAsState()
  var onlyUnread by remember { mutableStateOf(false) }
  val scope = core.scope
  ConnectionPanel(I18n.text("center.title"), Icons.Outlined.Notifications, { core.notifications.open.value = false }) {
    TextButton(onClick = { core.notifications.open.value = false; core.nav.openSettings("notifications") }) { Text(I18n.text("notification.settings")) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      FilterChip(onlyUnread, { onlyUnread = !onlyUnread }, { Text(I18n.text("center.unread")) })
      TextButton(onClick = core.notifications::readAll) { Text(I18n.text("center.readAll")) }
      TextButton(onClick = core.notifications::clearRead) { Text(I18n.text("center.clearRead")) }
    }
    if (conflicts.isNotEmpty()) FilledTonalButton(onClick = { core.notifications.open.value = false; core.notifications.conflictsOpen.value = true }) { Text(I18n.text("center.conflictsCount", "count" to conflicts.size)) }
    val visible = notices.filter { preferences.allows(it.kind, it.pluginId ?: (it.open as? WidgetAction.Open)?.pluginId, it.reminderEvent) && core.notifications.isVisible(it, activePlugins.map { p -> p.id }.toSet()) && (!onlyUnread || !it.read) }
    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (visible.isEmpty()) item { Text(I18n.text(if (onlyUnread) "center.noUnread" else "center.empty")) }
      items(visible, key = { it.id }) { notice ->
        Surface(shape = RoundedCornerShape(20.dp), color = if (notice.read) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh) {
          Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(notice.title ?: if (notice.operation != null && notice.reason != null) FeatureProblem(notice.operation, notice.reason).title() else if (notice.operation != null) I18n.text("errors.operation.${notice.operation}") else I18n.text("center.kind.${notice.kind}"), style = MaterialTheme.typography.titleSmall)
            Text(notice.reason?.let { I18n.text("errors.reason.$it") } ?: notice.detail ?: I18n.text("center.status.${notice.status}"), color = if (notice.status == NoticeStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            val time = Instant.fromEpochMilliseconds(notice.at).toLocalDateTime(TimeZone.currentSystemDefault())
            Text("${time.date.dayMonth()} ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}", style = MaterialTheme.typography.labelSmall)
            Row {
              if (!notice.read) TextButton(onClick = { core.notifications.markRead(notice.id) }) { Text(I18n.text("center.markRead")) }
              if (notice.kind == NoticeKind.CONFLICT) TextButton(onClick = { core.notifications.open.value = false; core.notifications.conflictsOpen.value = true }) { Text(I18n.text("center.view")) }
              if (notice.kind == NoticeKind.UPDATE) TextButton(onClick = { core.notifications.open.value = false; core.nav.openSettings(dev.akkraizen.berill.core.ui.settings.SettingsPage.Updates.id) }) { Text(I18n.text("center.view")) }
              if (notice.kind == NoticeKind.REMINDER) TextButton(onClick = {
                when (val action = notice.open) {
                  is WidgetAction.Open -> action.pluginId?.takeIf { id -> core.plugins.enabled.value.any { it.id == id } }?.let { core.nav.openPlugin(it, action.route, action.page) }
                  is WidgetAction.OpenNote -> core.nav.openNote(action.noteId)
                  else -> Unit
                }
                core.notifications.markRead(notice.id); core.notifications.open.value = false
              }) { Text(I18n.text("center.view")) }
              if (notice.kind == NoticeKind.ERROR) TextButton(onClick = {
                core.notifications.open.value = false
                when (notice.operation) {
                  FailureOperation.SEARCH -> core.connections.searchOpen.value = true
                  FailureOperation.DAY -> core.connections.dayOpen.value = true
                  FailureOperation.TRASH, FailureOperation.RESTORE, FailureOperation.DELETE -> core.connections.trashOpen.value = true
                  FailureOperation.HEALTH -> scope.launch { core.connections.errors.runSuspending(FailureOperation.HEALTH) { core.healthImport?.refresh() } }
                  FailureOperation.REMINDERS -> scope.launch { core.connections.errors.runSuspending(FailureOperation.REMINDERS) { core.reminders.refresh(core.plugins) } }
                  else -> core.nav.openSettings("data")
                }
              }) { Text(I18n.text("center.view")) }
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun ConflictsDialog(core: BerillCore) {
  LaunchedEffect(core) { core.editorSessions.flushNow() }
  val conflicts by core.store.conflicts.state.collectAsState()
  var stale by remember { mutableStateOf<String?>(null) }
  var confirm by remember { mutableStateOf<Pair<String, ConflictChoice>?>(null) }
  fun resolve(id: String, choice: ConflictChoice) {
    core.connections.errors.run(FailureOperation.COMMAND) { if (!core.store.resolveConflict(id, choice)) stale = id else stale = null }
  }
  ConnectionPanel(I18n.text("center.conflicts"), Icons.Outlined.SyncProblem, { core.notifications.conflictsOpen.value = false }) {
    Text(I18n.text("center.conflictsHint"), style = MaterialTheme.typography.bodySmall)
    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (conflicts.isEmpty()) item { Text(I18n.text("center.noConflicts")) }
      items(conflicts, key = { it.id }) { conflict ->
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
          Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val label = listOf("title", "name", "text", "word").firstNotNullOfOrNull { key -> (conflict.local.fields[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() } }
            Text(label?.take(120) ?: conflict.local.id, style = MaterialTheme.typography.titleSmall)
            Text("${conflict.local.collection} · ${conflict.local.id}", style = MaterialTheme.typography.labelSmall)
            if (stale == conflict.id) Text(I18n.text("center.conflictStale"), color = MaterialTheme.colorScheme.error)
            dev.akkraizen.berill.api.ui.layout.TwoPane(breakpoint = 560.dp, leftWeight = 1f,
              left = { ConflictVersion(ConflictChoice.LOCAL, conflict.local, if (conflict.deletion) conflict.local.fields.keys else conflict.fields.toSet()) { if (conflict.local.deleted) confirm = conflict.id to ConflictChoice.LOCAL else resolve(conflict.id, ConflictChoice.LOCAL) } },
              right = { ConflictVersion(ConflictChoice.REMOTE, conflict.remote, if (conflict.deletion) conflict.remote.fields.keys else conflict.fields.toSet()) { if (conflict.remote.deleted) confirm = conflict.id to ConflictChoice.REMOTE else resolve(conflict.id, ConflictChoice.REMOTE) } })
            TextButton(onClick = { resolve(conflict.id, ConflictChoice.MERGED) }) { Text(I18n.text("center.keepCurrent")) }
          }
        }
      }
    }
  }
  confirm?.let { (id, choice) -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text(I18n.text("center.deleteQuestion")) },
    confirmButton = { TextButton(onClick = { confirm = null; resolve(id, choice) }) { Text(I18n.text("ui.save")) } },
    dismissButton = { TextButton(onClick = { confirm = null }) { Text(I18n.text("ui.cancel")) } }) }
}

@Composable
private fun ConflictVersion(choice: ConflictChoice, record: dev.akkraizen.berill.protocol.SyncRecord, fields: Set<String>, choose: () -> Unit) {
  Text(I18n.text("center.choice.$choice"), style = MaterialTheme.typography.titleSmall)
  dev.akkraizen.berill.api.ui.layout.BoundedList {
    SelectionContainer { Text(if (record.deleted) I18n.text("center.deleted") else fields.joinToString("\n\n") { key -> "$key:\n" + ((record.fields[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: record.fields[key].toString()) }) }
  }
  TextButton(onClick = choose) { Text(I18n.text("center.useVersion")) }
}
