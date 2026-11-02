package dev.akkraizen.berill.plugins.health.data

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.fields.clockText
import dev.akkraizen.berill.api.ui.fields.parseClock
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.health.HealthPlugin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable

@Serializable
enum class MedicationKind { MEDICATION, SUPPLEMENT }

@Serializable
data class Medication(
  val name: String = "",
  val kind: MedicationKind = MedicationKind.MEDICATION,
  val dose: String? = null,
  val times: List<String> = emptyList(),
  val everyDays: Int = 1,
  val start: String? = null,
  val end: String? = null,
  val active: Boolean = true,
  val notes: String? = null,
  val createdAt: Long = 0
) {
  fun isScheduledOn(date: LocalDate): Boolean {
    if (!active) return false
    val from = start.toDateOrNull()
    val to = end.toDateOrNull()
    if (from != null && date < from) return false
    if (to != null && date > to) return false
    if (everyDays > 1) {
      val anchor = from ?: return true
      return anchor.daysUntil(date) % everyDays == 0
    }
    return true
  }

  fun sortedTimes(): List<String> = times.mapNotNull { parseClock(it) }.sorted().map { it.clockText() }.distinct()
}

@Serializable
enum class IntakeStatus { TAKEN, SKIPPED }

@Serializable
data class Intake(
  val medication: String = "",
  val date: String = "",
  val time: String? = null,
  val status: IntakeStatus = IntakeStatus.TAKEN,
  val at: Long = 0
)

fun intakeId(medicationId: String, date: String, time: String?): String = "$medicationId|$date|${time ?: ANY_TIME}"

private const val ANY_TIME = "any"

data class Dose(val medicationId: String, val medication: Medication, val date: String, val time: String?, val intake: Intake?) {
  val id: String get() = intakeId(medicationId, date, time)
}

fun dosesOn(date: LocalDate, medications: List<Pair<String, Medication>>, intakes: Map<String, Intake>): List<Dose> =
  medications.filter { (_, medication) -> medication.isScheduledOn(date) }
    .flatMap { (id, medication) ->
      medication.sortedTimes().ifEmpty { listOf(null) }.map { time ->
        Dose(id, medication, date.toString(), time, intakes[intakeId(id, date.toString(), time)])
      }
    }
    .sortedWith(compareBy({ it.time ?: "99:99" }, { it.medication.name.lowercase() }))

@Serializable
enum class VitalType(val min: Double, val max: Double, val secondMin: Double? = null, val secondMax: Double? = null) {
  PULSE(20.0, 250.0),
  BLOOD_PRESSURE(50.0, 260.0, 30.0, 180.0),
  TEMPERATURE(30.0, 45.0),
  OXYGEN(50.0, 100.0),
  GLUCOSE(1.0, 35.0);

  val paired: Boolean get() = secondMin != null
}

@Serializable
data class VitalEntry(
  val type: VitalType = VitalType.PULSE,
  val date: String = "",
  val time: String? = null,
  val value: Double = 0.0,
  val value2: Double? = null,
  val note: String? = null,
  val createdAt: Long = 0
) {
  fun problem(): String? {
    if (date.toDateOrNull() == null) return "A valid date is required"
    if (time != null && parseClock(time) == null) return "Time must be HH:MM"
    if (!value.isFinite() || value !in type.min..type.max) return "${type.name} must be between ${type.min} and ${type.max}"
    if (type.paired) {
      val second = value2 ?: return "${type.name} needs both values"
      if (!second.isFinite() || second !in type.secondMin!!..type.secondMax!!) return "Second value must be between ${type.secondMin} and ${type.secondMax}"
      if (second >= value) return "Diastolic pressure must be lower than systolic"
    } else if (value2 != null) return "${type.name} takes a single value"
    return null
  }
}

@Serializable
data class Symptom(val name: String = "", val severity: Int = 1)

@Serializable
data class WellbeingDay(
  val date: String = "",
  val wellbeing: Int? = null,
  val mood: Int? = null,
  val energy: Int? = null,
  val symptoms: List<Symptom> = emptyList(),
  val note: String? = null
) {
  fun problem(): String? {
    if (date.toDateOrNull() == null) return "A valid date is required"
    if (listOfNotNull(wellbeing, mood, energy).any { it !in RATINGS }) return "Ratings must be between 1 and 5"
    if (symptoms.any { it.name.isBlank() || it.severity !in SEVERITIES }) return "Symptoms need a name and severity 1–3"
    return null
  }

  val isEmpty: Boolean get() = wellbeing == null && mood == null && energy == null && symptoms.isEmpty() && note.isNullOrBlank()
}

@Serializable
data class SleepEntry(
  val date: String = "",
  val bedtime: String = "",
  val wake: String = "",
  val quality: Int? = null,
  val note: String? = null
) {
  fun minutes(): Int? {
    val start = parseClock(bedtime) ?: return null
    val end = parseClock(wake) ?: return null
    val diff = (end.hour * 60 + end.minute - start.hour * 60 - start.minute + DAY_MINUTES) % DAY_MINUTES
    return diff.takeIf { it > 0 }
  }

  fun problem(): String? {
    if (date.toDateOrNull() == null) return "A valid date is required"
    if (parseClock(bedtime) == null || parseClock(wake) == null) return "Bedtime and wake time must be HH:MM"
    if (minutes() == null) return "Bedtime and wake time must differ"
    if (quality != null && quality !in RATINGS) return "Quality must be between 1 and 5"
    return null
  }
}

@Serializable
data class ActivityDay(
  val date: String = "",
  val steps: Long? = null,
  val sleepMinutes: Long? = null,
  val source: String? = null,
  val manualSteps: Boolean = false
)

val RATINGS = 1..5
val SEVERITIES = 1..3
private const val DAY_MINUTES = 24 * 60
const val SOURCE_HEALTH_CONNECT = "health_connect"

internal fun medications(ctx: PluginContext) = ctx.data.collection(HealthPlugin.MEDICATIONS, Medication.serializer())

internal fun intakes(ctx: PluginContext) = ctx.data.collection(HealthPlugin.INTAKES, Intake.serializer())

internal fun vitals(ctx: PluginContext) = ctx.data.collection(HealthPlugin.VITALS, VitalEntry.serializer())

internal fun wellbeing(ctx: PluginContext) = ctx.data.collection(HealthPlugin.WELLBEING, WellbeingDay.serializer())

internal fun sleepLog(ctx: PluginContext) = ctx.data.collection(HealthPlugin.SLEEP, SleepEntry.serializer())

internal fun activity(ctx: PluginContext) = ctx.data.collection(HealthPlugin.ACTIVITY, ActivityDay.serializer())
