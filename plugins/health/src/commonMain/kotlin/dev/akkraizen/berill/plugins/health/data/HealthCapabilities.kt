package dev.akkraizen.berill.plugins.health.data

import dev.akkraizen.berill.api.HealthDataSink
import dev.akkraizen.berill.api.HealthDay
import dev.akkraizen.berill.api.HealthSummarySource
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.Reminder
import dev.akkraizen.berill.api.ReminderSource
import dev.akkraizen.berill.api.ui.fields.parseClock
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.plugins.health.HealthPlugin
import dev.akkraizen.berill.plugins.health.i18n.HealthText
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal class HealthActivity(private val ctx: PluginContext) : HealthDataSink, HealthSummarySource {
  override fun accept(day: HealthDay) {
    if (day.steps == null && day.sleepMinutes == null) return
    val days = activity(ctx)
    val old = days.get(day.date)
    val next = ActivityDay(day.date, day.steps ?: old?.steps, day.sleepMinutes ?: old?.sleepMinutes, SOURCE_HEALTH_CONNECT)
    if (next != old) days.save(day.date, next)
  }

  override fun day(date: LocalDate): HealthDay? = summary(ctx, date)
}

internal fun summary(ctx: PluginContext, date: LocalDate): HealthDay? {
  val key = date.toString()
  val imported = activity(ctx).get(key)
  val sleep = imported?.sleepMinutes ?: sleepLog(ctx).get(key)?.minutes()?.toLong()
  val steps = imported?.steps
  if (steps == null && sleep == null) return null
  return HealthDay(key, steps, sleep)
}

internal class MedicationReminders(private val ctx: PluginContext) : ReminderSource {
  override fun reminders(): List<Reminder> {
    val zone = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val today = now.toLocalDateTime(zone).date
    val meds = medications(ctx).all().map { it.id to it.value }
    val done = intakes(ctx).all().associate { it.id to it.value }
    return (0..1).flatMap { offset ->
      val date = today.plus(offset, DateTimeUnit.DAY)
      dosesOn(date, meds, done).mapNotNull { dose ->
        val clock = parseClock(dose.time) ?: return@mapNotNull null
        if (dose.intake != null) return@mapNotNull null
        val at = LocalDateTime(date, clock).toInstant(zone)
        if (at <= now) return@mapNotNull null
        Reminder(
          "${ctx.pluginId}:${dose.id}",
          HealthText.reminderTitle(dose.medication.name)(),
          dose.medication.dose?.takeIf { it.isNotBlank() } ?: HealthText.reminderBody(),
          at.toEpochMilliseconds(),
          WidgetAction.Open(page = HealthPlugin.PAGE_TODAY, pluginId = ctx.pluginId),
          pluginId = ctx.pluginId
        )
      }
    }
  }
}
