package dev.akkraizen.berill.plugins.planner.data

import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.plugins.planner.PlannerPlugin
import kotlinx.datetime.*
import kotlin.time.Clock

internal class PlannerReminders(private val ctx: PluginContext) : ReminderSource {
  override fun reminders(): List<Reminder> = ctx.data.collection(PlannerPlugin.TASKS, PlannerTask.serializer()).all().mapNotNull { item ->
    if (item.value.done) return@mapNotNull null
    val date = item.value.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
    val parsed = parse(item.value.text)
    val time = parsed.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime(9, 0)
    val instant = LocalDateTime(date, time).toInstant(TimeZone.currentSystemDefault())
    if (instant <= Clock.System.now()) return@mapNotNull null
    Reminder("planner:${item.id}", parsed.title, item.value.date.orEmpty(), instant.toEpochMilliseconds(), WidgetAction.Open(pluginId = ctx.pluginId))
  }
}
