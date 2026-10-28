package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.DoubleField
import dev.akkraizen.berill.api.ui.fields.EditableList
import dev.akkraizen.berill.api.ui.parseAmount
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.workouts.data.DEFAULT_BAR
import dev.akkraizen.berill.plugins.workouts.data.DEFAULT_PLATES
import dev.akkraizen.berill.plugins.workouts.data.DEFAULT_ROUNDING
import dev.akkraizen.berill.plugins.workouts.data.formatKg
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private const val BAR_KEY = "bar"
private const val PLATES_KEY = "plates"
private const val ROUNDING_KEY = "rounding"
private const val MAXES_KEY = "maxes"

internal data class WorkoutPrefs(
  val bar: Double,
  val plates: List<Double>,
  val rounding: Double,
  val maxes: Map<String, Double>
)

private fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.doubleOrNull

private fun JsonElement?.asPlates(): List<Double>? =
  (this as? JsonArray)
    ?.mapNotNull { it.asDouble() }
    ?.filter { it > 0 }
    ?.takeIf { it.isNotEmpty() }

private fun JsonElement?.asMaxes(): Map<String, Double> = (this as? JsonObject)?.mapNotNull { (k, v) -> v.asDouble()?.let { k to it } }?.toMap().orEmpty()

@Composable
internal fun rememberWorkoutPrefs(ctx: PluginContext): WorkoutPrefs {
  val s = ctx.settings
  val bar by remember(ctx) { s.observe(BAR_KEY) }.collectAsState(s.get(BAR_KEY))
  val plates by remember(ctx) { s.observe(PLATES_KEY) }.collectAsState(s.get(PLATES_KEY))
  val rounding by remember(ctx) { s.observe(ROUNDING_KEY) }.collectAsState(s.get(ROUNDING_KEY))
  val maxes by remember(ctx) { s.observe(MAXES_KEY) }.collectAsState(s.get(MAXES_KEY))
  return WorkoutPrefs(
    bar = bar.asDouble() ?: DEFAULT_BAR,
    plates = plates.asPlates() ?: DEFAULT_PLATES,
    rounding = rounding.asDouble() ?: DEFAULT_ROUNDING,
    maxes = maxes.asMaxes()
  )
}

internal fun workoutPrefs(ctx: PluginContext): WorkoutPrefs {
  val s = ctx.settings
  return WorkoutPrefs(
    bar = s.get(BAR_KEY).asDouble() ?: DEFAULT_BAR,
    plates = s.get(PLATES_KEY).asPlates() ?: DEFAULT_PLATES,
    rounding = s.get(ROUNDING_KEY).asDouble() ?: DEFAULT_ROUNDING,
    maxes = s.get(MAXES_KEY).asMaxes()
  )
}

internal fun setBar(ctx: PluginContext, value: Double) = ctx.settings.set(BAR_KEY, JsonPrimitive(value))

internal fun setRounding(ctx: PluginContext, value: Double) = ctx.settings.set(ROUNDING_KEY, JsonPrimitive(value))

internal fun setPlates(ctx: PluginContext, plates: List<Double>) = ctx.settings.set(PLATES_KEY, JsonArray(plates.distinct().sortedDescending().map { JsonPrimitive(it) }))

internal fun setMax(ctx: PluginContext, exercise: String, value: Double?) {
  val name = exercise.trim()
  if (name.isEmpty()) return
  val current = ctx.settings.get(MAXES_KEY).asMaxes().filterKeys { it.trim().lowercase() != name.lowercase() }
  val next = if (value == null) current else current + (name to value)
  ctx.settings.set(MAXES_KEY, JsonObject(next.mapValues { JsonPrimitive(it.value) }))
}

private val ROUNDING_STEPS = listOf(0.5, 1.0, 1.25, 2.5, 5.0)

@Composable
internal fun WorkoutsSettings(ctx: PluginContext) {
  val prefs = rememberWorkoutPrefs(ctx)
  SectionCard(WorkoutsText.equipment(), Icons.Outlined.FitnessCenter) {
    DoubleField(prefs.bar, { it?.let { v -> setBar(ctx, v) } }, WorkoutsText.defaultBar())
    Text(
      WorkoutsText.availablePlates(),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    EditableList(
      items = prefs.plates.map(::formatKg),
      onChange = { list ->
        setPlates(ctx, list.mapNotNull { parseAmount(it) }.filter { it > 0 })
      },
      placeholder = WorkoutsText.platePlaceholder(),
      onReset = { setPlates(ctx, DEFAULT_PLATES) },
      draftKey = "workouts/plates"
    )
    Text(
      WorkoutsText.rounding(),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ChoiceChips(ROUNDING_STEPS, prefs.rounding, { formatKg(it) }, { setRounding(ctx, it) })
  }
  MaxesCard(ctx, prefs)
}

@Composable
internal fun MaxesCard(ctx: PluginContext, prefs: WorkoutPrefs) {
  SectionCard(WorkoutsText.maxes(), Icons.Outlined.TrendingUp) {
    Text(
      WorkoutsText.maxesHint(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (prefs.maxes.isEmpty()) EmptyHint(WorkoutsText.noMaxes())
    prefs.maxes.entries
      .sortedBy { it.key.lowercase() }
      .forEach { (name, value) ->
        Row(
          Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(name, modifier = Modifier.weight(1f))
          Text(
            WorkoutsText.kgValue(formatKg(value))(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall
          )
          IconButton(onClick = { setMax(ctx, name, null) }) {
            Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
  }
}
