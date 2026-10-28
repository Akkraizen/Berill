package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.layout.FieldWithActions
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.parseAmount
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.plugins.workouts.data.estimateOneRepMax
import dev.akkraizen.berill.plugins.workouts.data.formatKg
import dev.akkraizen.berill.plugins.workouts.data.loadPlates
import dev.akkraizen.berill.plugins.workouts.data.repsAtPercent
import dev.akkraizen.berill.plugins.workouts.data.roundTo
import dev.akkraizen.berill.plugins.workouts.i18n.CatalogText
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText

@Composable
internal fun CalculatorPage(ctx: PluginContext) {
  val prefs = rememberWorkoutPrefs(ctx)
  PluginList {
    item {
      TwoPane(
        left = {
          OneRepMaxCard(ctx, prefs)
          MaxesCard(ctx, prefs)
        },
        right = { PlatesCard(prefs) },
        leftWeight = 1f
      )
    }
  }
}

private val PERCENTS = (100 downTo 50 step 5).toList()

@Composable
private fun OneRepMaxCard(ctx: PluginContext, prefs: WorkoutPrefs) {
  var weight by rememberDraft("workouts/one-rep-max/weight")
  var reps by rememberDraft("workouts/one-rep-max/reps")
  var exercise by rememberDraft("workouts/one-rep-max/exercise")
  var picking by rememberSaveable { mutableStateOf(false) }
  if (picking) {
    ExercisePickerDialog(ctx, onDismiss = { picking = false }) { selected ->
      exercise = selected.name
      picking = false
    }
  }
  val w = parseAmount(weight)
  val r = reps.toIntOrNull()
  val max = if (w != null && r != null) estimateOneRepMax(w, r) else null

  SectionCard(WorkoutsText.oneRepMax(), Icons.Outlined.Calculate) {
    Text(
      WorkoutsText.oneRepMaxHint(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FieldsRow {
      BerillTextField(
        weight,
        { weight = it.take(7) },
        label = { Text(WorkoutsText.liftedWeight(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = weight.isNotBlank() && w == null,
        modifier = Modifier.fillMaxWidth()
      )
      BerillTextField(
        reps,
        { reps = it.filter(Char::isDigit).take(2) },
        label = { Text(WorkoutsText.liftedReps(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )
    }
    FieldWithActions(
      field = {
        BerillTextField(
          exercise,
          { exercise = it },
          label = { Text(WorkoutsText.exercise(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          onSubmit = { if (exercise.isNotBlank() && max != null) setMax(ctx, exercise, roundTo(max, 0.5)) }
        )
      },
      actions = {
        IconButton(onClick = { picking = true }) {
          Icon(Icons.Outlined.MenuBook, contentDescription = CatalogText.addFromCatalog())
        }
        FilledTonalButton(enabled = exercise.isNotBlank() && max != null, onClick = { max?.let { setMax(ctx, exercise, roundTo(it, 0.5)) } }) {
          Text(WorkoutsText.saveAsMax())
        }
      }
    )
    if (max != null) {
      Text(
        WorkoutsText.estimatedMax(formatKg(roundTo(max, 0.5)))(),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary
      )
      Text(WorkoutsText.percentTable(), style = MaterialTheme.typography.titleSmall)
      Column {
        PERCENTS.forEachIndexed { i, p ->
          if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$p%", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelLarge)
            Text(
              WorkoutsText.kgValue(formatKg(roundTo(max * p / 100.0, prefs.rounding)))(),
              modifier = Modifier.weight(1f),
              fontWeight = FontWeight.SemiBold
            )
            Text(
              WorkoutsText.repsApprox(repsAtPercent(p.toDouble()))(),
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Text(
        WorkoutsText.saveMaxFor(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun PlatesCard(prefs: WorkoutPrefs) {
  var target by rememberDraft("workouts/plates/target", "100")
  var bar by rememberSaveable(prefs.bar) { mutableStateOf(formatKg(prefs.bar)) }
  val t = parseAmount(target)
  val b = parseAmount(bar)

  SectionCard(WorkoutsText.plates(), Icons.Outlined.FitnessCenter) {
    FieldsRow {
      BerillTextField(
        target,
        { target = it.take(7) },
        label = { Text(WorkoutsText.targetWeight(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = target.isNotBlank() && t == null,
        modifier = Modifier.fillMaxWidth()
      )
      BerillTextField(
        bar,
        { bar = it.take(6) },
        label = { Text(WorkoutsText.barWeight(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = bar.isNotBlank() && b == null,
        modifier = Modifier.fillMaxWidth()
      )
    }
    if (t != null && b != null) {
      val load = loadPlates(t, b, prefs.plates)
      Text(WorkoutsText.perSide(), style = MaterialTheme.typography.titleSmall)
      if (load.perSide.isEmpty()) {
        Text(WorkoutsText.justBar(), color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        BarbellPicture(load.perSide)
        Text(load.perSide.joinToString(" + ") { formatKg(it) }, style = MaterialTheme.typography.titleMedium)
      }
      Text(WorkoutsText.totalLoaded(formatKg(load.total))(), color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (load.remainder > 0.001) {
        Text(WorkoutsText.remainder(formatKg(load.remainder))(), color = MaterialTheme.colorScheme.error)
      }
    }
  }
}

@Composable
private fun BarbellPicture(plates: List<Double>) {
  val colors = MaterialTheme.colorScheme
  val heaviest = plates.maxOrNull() ?: 1.0
  Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    Box(
      Modifier.width(36.dp)
        .height(10.dp)
        .clip(MaterialTheme.shapes.extraSmall)
        .background(colors.outline)
    )
    Box(
      Modifier.width(8.dp)
        .height(28.dp)
        .clip(MaterialTheme.shapes.extraSmall)
        .background(colors.onSurfaceVariant)
    )
    plates.forEach { plate ->
      val (fill, text) = plateColors(plate, colors.surfaceContainerHighest, colors.onSurface)
      val height = (48 + 72 * (plate / maxOf(heaviest, 20.0))).coerceAtMost(124.0).dp
      Box(
        Modifier.width(if (plate >= 10) 22.dp else 16.dp)
          .height(height)
          .clip(MaterialTheme.shapes.extraSmall)
          .background(fill)
          .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center
      ) {
        Text(
          formatKg(plate),
          color = text,
          style = MaterialTheme.typography.labelSmall,
          textAlign = TextAlign.Center,
          maxLines = 1,
          softWrap = false
        )
      }
    }

    Box(
      Modifier.width(24.dp)
        .height(10.dp)
        .clip(MaterialTheme.shapes.extraSmall)
        .background(colors.outline)
    )
  }
}

private fun plateColors(plate: Double, neutral: Color, onNeutral: Color): Pair<Color, Color> =
  when (plate) {
    25.0 -> Color(0xFFD32F2F) to Color.White
    20.0 -> Color(0xFF1E63D6) to Color.White
    15.0 -> Color(0xFFF9A825) to Color.Black
    10.0 -> Color(0xFF2E7D32) to Color.White
    5.0 -> Color(0xFFF5F5F5) to Color.Black
    2.5 -> Color(0xFF263238) to Color.White
    1.25,
    1.0 -> Color(0xFF9E9E9E) to Color.Black

    else -> neutral to onNeutral
  }
