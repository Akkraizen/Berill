package dev.akkraizen.berill.core.notifications

import dev.akkraizen.berill.api.Reminder
import dev.akkraizen.berill.api.ReminderEvent
import dev.akkraizen.berill.api.widgets.WidgetAction
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class PluginNotificationPreferences(val enabled: Boolean = true, val sound: Boolean = true, val hiddenEvents: Set<ReminderEvent> = emptySet())
@Serializable
data class NotificationPreferences(val enabled: Boolean = true, val sound: Boolean = true,
  val hiddenKinds: Set<NoticeKind> = emptySet(), val quietHours: Boolean = false, val quietStart: Int = 22 * 60, val quietEnd: Int = 8 * 60,
  val plugins: Map<String, PluginNotificationPreferences> = emptyMap()) {
  fun quietAt(atMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
    if (!quietHours) return false
    val time = Instant.fromEpochMilliseconds(atMillis).toLocalDateTime(zone).time
    val minute = time.hour * 60 + time.minute
    val start = quietStart.coerceIn(0, 1439); val end = quietEnd.coerceIn(0, 1439)
    return if (start == end) true else if (start < end) minute >= start && minute < end else minute >= start || minute < end
  }
  fun allows(kind: NoticeKind, pluginId: String? = null, event: ReminderEvent = ReminderEvent.REMINDER): Boolean {
    val rule = plugins[pluginId] ?: PluginNotificationPreferences()
    return enabled && kind !in hiddenKinds && rule.enabled && (kind != NoticeKind.REMINDER || event !in rule.hiddenEvents)
  }
  fun allows(reminder: Reminder): Boolean = allows(NoticeKind.REMINDER, reminder.owner(), reminder.event)
  fun silent(reminder: Reminder, atMillis: Long): Boolean = reminder.silent || !sound || plugins[reminder.owner()]?.sound == false || quietAt(atMillis)
}
fun Reminder.owner(): String? = pluginId ?: (open as? WidgetAction.Open)?.pluginId
