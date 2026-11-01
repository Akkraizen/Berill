package dev.akkraizen.berill.core.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.notifications.*
import kotlinx.coroutines.launch

@Composable
internal fun NotificationSettings(core: BerillCore) {
  val prefs by core.notificationPreferences.state.collectAsState()
  val plugins by core.plugins.enabled.collectAsState()
  val daily by core.reminders.enabled.collectAsState()
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  SettingSwitch(I18n.text("notification.enabled"), null, prefs.enabled) { core.notificationPreferences.set(prefs.copy(enabled = it)) }
  SettingSwitch(I18n.text("notification.sound"), I18n.text("notification.soundHint"), prefs.sound) { core.notificationPreferences.set(prefs.copy(sound = it)) }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(I18n.text("connections.reminders"), Modifier.weight(1f))
    Switch(daily, enabled = !busy, onCheckedChange = { value -> scope.launch {
      busy = true
      try { core.connections.errors.runSuspending(dev.akkraizen.berill.core.connections.FailureOperation.REMINDERS) { core.reminders.setEnabled(value) } }
      finally { busy = false }
    } })
  }
  SettingSwitch(I18n.text("notification.quiet"), I18n.text("notification.quietHint"), prefs.quietHours) { core.notificationPreferences.set(prefs.copy(quietHours = it)) }
  if (prefs.quietHours) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    QuietTime(I18n.text("notification.from"), prefs.quietStart, Modifier.weight(1f)) { core.notificationPreferences.set(prefs.copy(quietStart = it)) }
    QuietTime(I18n.text("notification.until"), prefs.quietEnd, Modifier.weight(1f)) { core.notificationPreferences.set(prefs.copy(quietEnd = it)) }
  }
  Text(I18n.text("notification.events"), style = MaterialTheme.typography.titleMedium)
  NoticeKind.entries.filterNot { it == NoticeKind.SYNC }.forEach { kind ->
    SettingSwitch(I18n.text("center.kind.$kind"), null, kind !in prefs.hiddenKinds) {
      core.notificationPreferences.set(prefs.copy(hiddenKinds = if (it) prefs.hiddenKinds - kind else prefs.hiddenKinds + kind))
    }
  }
  Text(I18n.text("notification.plugins"), style = MaterialTheme.typography.titleMedium)
  val sources = plugins.filter { p -> p.provides(core.plugins.context(p)).any { it is ReminderSource || it is DeadlineSource } || p.id == "workouts" }
  if (sources.isEmpty()) Text(I18n.text("notification.noPlugins"), color = MaterialTheme.colorScheme.onSurfaceVariant)
  sources.forEach { plugin ->
    val rule = prefs.plugins[plugin.id] ?: PluginNotificationPreferences()
    fun save(value: PluginNotificationPreferences) = core.notificationPreferences.set(prefs.copy(plugins = prefs.plugins + (plugin.id to value)))
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
      Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingSwitch(plugin.title, null, rule.enabled) { save(rule.copy(enabled = it)) }
        if (rule.enabled) {
          SettingSwitch(I18n.text("notification.sound"), null, rule.sound) { save(rule.copy(sound = it)) }
          ReminderEvent.entries.forEach { event ->
            SettingSwitch(I18n.text("notification.event.$event"), null, event !in rule.hiddenEvents) { save(rule.copy(hiddenEvents = if (it) rule.hiddenEvents - event else rule.hiddenEvents + event)) }
          }
        }
      }
    }
  }
  TextButton(onClick = { core.notificationPreferences.reset() }) { Text(I18n.text("workspace.reset")) }
}

@Composable
private fun QuietTime(label: String, minute: Int, modifier: Modifier, onChange: (Int) -> Unit) {
  var text by remember(minute) { mutableStateOf("${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}") }
  fun parse(value: String): Int? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val m = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return h * 60 + m
  }
  val valid = parse(text) != null
  OutlinedTextField(text, { value -> text = value; parse(value)?.let(onChange) }, modifier,
    label = { Text(label) }, singleLine = true, isError = !valid,
    supportingText = if (valid) null else ({ Text(I18n.text("notification.timeFormat")) }))
}
