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
import kotlin.time.Duration.Companion.minutes
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
    val steps = if (old?.manualSteps == true) old.steps else day.steps ?: old?.steps
    val next = ActivityDay(day.date, steps, day.sleepMinutes ?: old?.sleepMinutes, SOURCE_HEALTH_CONNECT, old?.manualSteps == true)
    if (next != old) days.save(day.date, next)
  }

  override fun day(date: LocalDate): HealthDay? = summary(ctx, date)
}

internal fun saveManualSteps(ctx: PluginContext, date: String, steps: Long) {
  val days = activity(ctx)
  val old = days.get(date)
  days.save(date, ActivityDay(date, steps, old?.sleepMinutes, old?.source, manualSteps = true))
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
    val prefs = healthPrefs(ctx)
    if (!prefs.reminders) return emptyList()
    val lead = prefs.reminderLeadMinutes.coerceIn(0, 120).minutes
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
        val at = LocalDateTime(date, clock).toInstant(zone) - lead
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
