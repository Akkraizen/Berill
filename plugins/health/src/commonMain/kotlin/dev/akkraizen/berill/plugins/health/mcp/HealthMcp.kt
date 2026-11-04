package dev.akkraizen.berill.plugins.health.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.McpAction
import dev.akkraizen.berill.api.mcp.McpCollection
import dev.akkraizen.berill.api.mcp.McpErrorCode
import dev.akkraizen.berill.api.mcp.McpException
import dev.akkraizen.berill.api.mcp.mcpInput
import dev.akkraizen.berill.api.mcp.mcpOptionalString
import dev.akkraizen.berill.api.mcp.mcpString
import dev.akkraizen.berill.api.ui.fields.clockText
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.health.data.Dose
import dev.akkraizen.berill.plugins.health.data.dosesOn
import dev.akkraizen.berill.plugins.health.data.intakes
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.akkraizen.berill.api.mcp.McpSource
import dev.akkraizen.berill.api.mcp.ReadOnlyMcpCollection
import dev.akkraizen.berill.api.mcp.TypedMcpCollection
import dev.akkraizen.berill.api.mcp.mcpEnumSchema
import dev.akkraizen.berill.api.ui.fields.parseClock
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.health.HealthPlugin
import dev.akkraizen.berill.plugins.health.data.ActivityDay
import dev.akkraizen.berill.plugins.health.data.Intake
import dev.akkraizen.berill.plugins.health.data.IntakeStatus
import dev.akkraizen.berill.plugins.health.data.Medication
import dev.akkraizen.berill.plugins.health.data.MedicationKind
import dev.akkraizen.berill.plugins.health.data.SleepEntry
import dev.akkraizen.berill.plugins.health.data.VitalEntry
import dev.akkraizen.berill.plugins.health.data.VitalType
import dev.akkraizen.berill.plugins.health.data.WellbeingDay
import dev.akkraizen.berill.plugins.health.data.intakeId
import dev.akkraizen.berill.plugins.health.data.medications

internal class HealthMcp(private val ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId

  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(
      ctx, HealthPlugin.MEDICATIONS, Medication.serializer(),
      fieldSchemas = mapOf("kind" to mcpEnumSchema(MedicationKind.serializer(), MedicationKind.entries)),
      normalize = { old, next -> next.copy(times = next.sortedTimes(), start = next.start.normalizedDate(), end = next.end.normalizedDate(), createdAt = old?.createdAt ?: nowMillis()) },
      validate = {
        require(it.name.isNotBlank()) { "Medication name is required" }
        require(it.times.all { time -> parseClock(time) != null }) { "Times must be HH:MM" }
        require(it.everyDays in 1..365) { "everyDays must be between 1 and 365" }
        require(it.start == null || it.start.toDateOrNull() != null) { "Invalid start date" }
        require(it.end == null || it.end.toDateOrNull() != null) { "Invalid end date" }
        val from = it.start.toDateOrNull()
        val to = it.end.toDateOrNull()
        require(from == null || to == null || from <= to) { "End date must not be before start date" }
      }
    ),
    TypedMcpCollection(
      ctx, HealthPlugin.INTAKES, Intake.serializer(),
      fieldSchemas = mapOf("status" to mcpEnumSchema(IntakeStatus.serializer(), IntakeStatus.entries)),
      createId = { intakeId(it.medication, it.date, it.time) },
      normalize = { old, next ->
        val value = next.copy(date = next.date.normalizedDate() ?: next.date, at = if (next.at > 0) next.at else nowMillis())
        require(old == null || (old.medication == value.medication && old.date == value.date && old.time == value.time)) { "Intake medication, date and time are immutable" }
        value
      },
      validate = {
        require(medications(ctx).get(it.medication) != null) { "Unknown medication id" }
        require(it.date.toDateOrNull() != null) { "A valid date is required" }
        require(it.time == null || parseClock(it.time) != null) { "Time must be HH:MM" }
      }
    ),
    TypedMcpCollection(
      ctx, HealthPlugin.VITALS, VitalEntry.serializer(),
      fieldSchemas = mapOf("type" to mcpEnumSchema(VitalType.serializer(), VitalType.entries)),
      normalize = { old, next -> next.copy(date = next.date.normalizedDate() ?: next.date, createdAt = old?.createdAt ?: nowMillis()) },
      validate = { entry -> entry.problem()?.let { throw IllegalArgumentException(it) } }
    ),
    TypedMcpCollection(
      ctx, HealthPlugin.WELLBEING, WellbeingDay.serializer(),
      createId = { it.date },
      normalize = { old, next ->
        val value = next.copy(date = next.date.normalizedDate() ?: next.date, symptoms = next.symptoms.map { it.copy(name = it.name.trim()) })
        require(old == null || old.date == value.date) { "Wellbeing date is immutable" }
        value
      },
      validate = { day -> day.problem()?.let { throw IllegalArgumentException(it) } }
    ),
    TypedMcpCollection(
      ctx, HealthPlugin.SLEEP, SleepEntry.serializer(),
      createId = { it.date },
      normalize = { old, next ->
        val value = next.copy(date = next.date.normalizedDate() ?: next.date)
        require(old == null || old.date == value.date) { "Sleep date is immutable" }
        value
      },
      validate = { entry -> entry.problem()?.let { throw IllegalArgumentException(it) } }
    ),
    TypedMcpCollection(
      ctx, HealthPlugin.ACTIVITY, ActivityDay.serializer(),
      createId = { it.date },
      normalize = { old, next ->
        val value = next.copy(date = next.date.normalizedDate() ?: next.date)
        require(old == null || old.date == value.date) { "Activity date is immutable" }
        value.copy(manualSteps = old?.manualSteps == true || value.steps != old?.steps)
      },
      validate = {
        require(it.date.toDateOrNull() != null) { "A valid date is required" }
        require(it.steps == null || it.steps in 0..200_000) { "Steps must be between 0 and 200000" }
        require(it.sleepMinutes == null || it.sleepMinutes in 0..1440) { "Sleep must be between 0 and 1440 minutes" }
      }
    )
  )

  override val collections: List<McpCollection> =
    editable + (HealthPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }

  override val actions: List<McpAction> = listOf(
    McpAction(
      "doses",
      "Lists the medication and supplement doses scheduled for a day with whether each was taken or skipped.",
      mcpInput { string("date", "Day YYYY-MM-DD, default today") },
      readOnly = true
    ) { args -> buildJsonObject {
      val date = dateArgument(args)
      put("date", date.toString())
      put("doses", JsonArray(doses(date).map(::doseJson)))
    } },
    McpAction(
      "mark_dose",
      "Marks a scheduled dose as TAKEN or SKIPPED, or clears the mark with NONE, the same way the checklist in the app does. " +
        "Without `time` it picks the first unmarked dose of that medication on the day.",
      mcpInput {
        string("medication", "Medication id or name", required = true)
        choice("status", "Mark", listOf("TAKEN", "SKIPPED", "NONE"), required = true)
        string("date", "Day YYYY-MM-DD, default today")
        string("time", "Scheduled time HH:MM")
      }
    ) { args -> markDose(args) }
  )

  private fun dateArgument(args: JsonObject): LocalDate =
    args.mcpOptionalString("date")?.takeIf { it.isNotBlank() }?.let { it.toDateOrNull() ?: throw McpException("Date must be YYYY-MM-DD") } ?: today()

  private fun doses(date: LocalDate): List<Dose> =
    dosesOn(date, medications(ctx).all().map { it.id to it.value }, intakes(ctx).all().associate { it.id to it.value })

  private fun doseJson(dose: Dose): JsonObject = buildJsonObject {
    put("medication", dose.medicationId)
    put("name", dose.medication.name)
    dose.medication.dose?.let { put("dose", it) }
    dose.time?.let { put("time", it) }
    put("status", dose.intake?.status?.name ?: "NONE")
  }

  private fun markDose(args: JsonObject): JsonObject {
    val date = dateArgument(args)
    val reference = args.mcpString("medication").trim()
    val all = medications(ctx).all()
    val medication = all.firstOrNull { it.id == reference } ?: all.filter { it.value.name.equals(reference, ignoreCase = true) }.let {
      when (it.size) {
        1 -> it.single()
        0 -> throw McpException("Medication not found", McpErrorCode.NOT_FOUND)
        else -> throw McpException("Several medications are called `$reference`; pass the id")
      }
    }
    val time = args.mcpOptionalString("time")?.takeIf { it.isNotBlank() }?.let { parseClock(it)?.clockText() ?: throw McpException("Time must be HH:MM") }
    val candidates = doses(date).filter { it.medicationId == medication.id }
    if (candidates.isEmpty()) throw McpException("${medication.value.name} is not scheduled on $date")
    val status = args.mcpString("status")
    val dose = when {
      time != null -> candidates.firstOrNull { it.time == time } ?: throw McpException("No dose at $time; scheduled: ${candidates.mapNotNull { it.time }}")
      status == "NONE" -> candidates.lastOrNull { it.intake != null } ?: candidates.first()
      else -> candidates.firstOrNull { it.intake == null } ?: candidates.last()
    }
    val log = intakes(ctx)
    when (status) {
      "NONE" -> log.delete(dose.id)
      "TAKEN", "SKIPPED" -> log.save(dose.id, Intake(dose.medicationId, dose.date, dose.time, IntakeStatus.valueOf(status), nowMillis()))
      else -> throw McpException("Status must be TAKEN, SKIPPED or NONE")
    }
    return buildJsonObject {
      put("date", date.toString())
      put("doses", JsonArray(doses(date).filter { it.medicationId == medication.id }.map(::doseJson)))
    }
  }
}

private fun String?.normalizedDate(): String? = this?.toDateOrNull()?.toString() ?: this?.takeIf { it.isNotBlank() }
