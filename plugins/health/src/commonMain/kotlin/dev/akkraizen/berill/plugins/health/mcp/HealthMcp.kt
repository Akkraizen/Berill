package dev.akkraizen.berill.plugins.health.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.McpCollection
import dev.akkraizen.berill.api.mcp.McpSource
import dev.akkraizen.berill.api.mcp.ReadOnlyMcpCollection
import dev.akkraizen.berill.api.mcp.TypedMcpCollection
import dev.akkraizen.berill.api.mcp.mcpEnumSchema
import dev.akkraizen.berill.api.ui.fields.parseClock
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.health.HealthPlugin
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

internal class HealthMcp(ctx: PluginContext) : McpSource {
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
    )
  )

  override val collections: List<McpCollection> =
    editable + (HealthPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}

private fun String?.normalizedDate(): String? = this?.toDateOrNull()?.toString() ?: this?.takeIf { it.isNotBlank() }
