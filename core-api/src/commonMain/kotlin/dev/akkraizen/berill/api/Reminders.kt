package dev.akkraizen.berill.api

import dev.akkraizen.berill.api.widgets.WidgetAction
import kotlinx.datetime.*
import kotlin.time.Clock

@kotlinx.serialization.Serializable
enum class ReminderEvent { REMINDER, DEADLINE, TIMER }

@kotlinx.serialization.Serializable
data class Reminder(val id: String, val title: String, val body: String, val atMillis: Long, val open: WidgetAction = WidgetAction.OpenHome, val pluginId: String? = null, val event: ReminderEvent = ReminderEvent.REMINDER, val silent: Boolean = false) {
  // Preserve the constructor used by already compiled external plugins.
  constructor(id: String, title: String, body: String, atMillis: Long, open: WidgetAction = WidgetAction.OpenHome) :
    this(id, title, body, atMillis, open, null, ReminderEvent.REMINDER, false)
}
interface ReminderSource : Capability { fun reminders(): List<Reminder> }
interface ReminderAccess {
  suspend fun schedule(reminder: Reminder)
  fun cancel(id: String)
}
object NoReminders : ReminderAccess {
  override suspend fun schedule(reminder: Reminder) {}
  override fun cancel(id: String) {}
}

class DailyReminderSource(private val ctx: PluginContext, private val title: () -> String, private val hours: List<Int>, private val needed: () -> Boolean) : ReminderSource {
  override fun reminders(): List<Reminder> {
    val now = Clock.System.now()
    val zone = TimeZone.currentSystemDefault()
    val date = now.toLocalDateTime(zone).date
    return (0..1).flatMap { day ->
      if (day == 0 && !needed()) return@flatMap emptyList()
      val target = date.plus(day, DateTimeUnit.DAY)
      hours.mapNotNull { hour ->
        val at = LocalDateTime(target, LocalTime(hour, 0)).toInstant(zone)
        if (at <= now) null else Reminder("${ctx.pluginId}:$target:$hour", title(), title(), at.toEpochMilliseconds(), WidgetAction.Open(pluginId = ctx.pluginId), pluginId = ctx.pluginId)
      }
    }
  }
}
