package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.api.ui.selection.DropdownField
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.StatisticsPeriod
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.workouts.data.*
import dev.akkraizen.berill.plugins.workouts.i18n.pluginI18n
import kotlin.math.*

private enum class MuscleComparison { PREVIOUS, PROGRAM }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MuscleBalanceCard(logs: List<WorkoutLog>, catalog: List<CatalogExercise>, programs: List<Item<Program>> = emptyList(),
  maxes: Map<String, Double> = emptyMap(), rounding: Double = DEFAULT_ROUNDING) {
  var days by rememberSaveable { mutableIntStateOf(30) }
  var metric by rememberSaveable { mutableStateOf(MuscleMetric.SETS) }
  var comparison by rememberSaveable { mutableStateOf(MuscleComparison.PREVIOUS) }
  var selectedProgramId by rememberSaveable { mutableStateOf<String?>(null) }
  var weekOffset by rememberSaveable { mutableIntStateOf(0) }
  val today = today()
  val week = workoutComparisonWeek(today, weekOffset)
  val choices = remember(programs) { programs.sortedBy { it.value.title.lowercase() } }
  val selectedProgram = choices.firstOrNull { it.id == selectedProgramId } ?: choices.firstOrNull()
  val programMode = comparison == MuscleComparison.PROGRAM
  val end = if (programMode) week.through else today
  val actualDays = if (programMode) (week.through.toEpochDays() - week.start.toEpochDays()).toInt() + 1 else days
  val actual = remember(logs, catalog, end, actualDays, metric) { muscleBalance(logs, catalog, end, actualDays, metric) }
  val plan = remember(selectedProgram, catalog, metric, maxes, rounding) { selectedProgram?.let { plannedMuscleLoad(it.value, catalog, metric, maxes, rounding) } }
  val balance = if (programMode && plan != null) actual.copy(previous = plan.values) else actual
  val currentLabel = pluginI18n.text(if (programMode) "workouts.radar.actualWeek" else "workouts.radar.current")
  val comparisonLabel = pluginI18n.text(if (programMode) "workouts.radar.plannedWeek" else "workouts.radar.previous")

  SectionCard(pluginI18n.text("workouts.radar.title"), Icons.Outlined.Insights) {
    ChoiceChips(MuscleComparison.entries, comparison, { pluginI18n.text("workouts.radar.compare.${it.name}") }, { comparison = it })
    if (programMode) {
      if (selectedProgram == null) { EmptyHint(pluginI18n.text("workouts.radar.noPrograms")); return@SectionCard }
      DropdownField(pluginI18n.text("workouts.radar.program"), choices, selectedProgram,
        { it.value.title.ifBlank { pluginI18n.text("workouts.radar.untitledProgram") } }, { selectedProgramId = it.id })
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { weekOffset-- }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, pluginI18n.text("workouts.radar.previousWeek")) }
        Text(pluginI18n.text("workouts.radar.weekRange", "start" to week.start.dayMonthShort(), "end" to week.end.dayMonthShort(), "year" to week.end.year),
          modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { weekOffset++ }, enabled = weekOffset < 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, pluginI18n.text("workouts.radar.nextWeek")) }
      }
      if (weekOffset < 0) TextButton(onClick = { weekOffset = 0 }) { Text(pluginI18n.text("workouts.radar.thisWeek")) }
    } else StatisticsPeriod(days) { days = it }
    ChoiceChips(MuscleMetric.entries, metric, { pluginI18n.text("workouts.radar.metric.${it.name}") }, { metric = it })
    Text(pluginI18n.text(if (programMode) "workouts.radar.programHint" else "workouts.radar.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val noVolumePlan = programMode && metric == MuscleMetric.VOLUME && plan != null && plan.missingVolumeSets > 0 && plan.values.all { it == 0.0 }
    if (noVolumePlan) EmptyHint(pluginI18n.text("workouts.radar.noVolumePlan"))
    else if ((balance.current + balance.previous).all { it == 0.0 }) EmptyHint(pluginI18n.text("workouts.radar.empty"))
    else {
      MuscleRadar(balance, metric, currentLabel, comparisonLabel)
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(currentLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Text(comparisonLabel, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
      }
    }
    if (balance.excludedSets > 0) Text(pluginI18n.text("workouts.radar.excluded", "count" to balance.excludedSets), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (programMode && plan != null) {
      if (plan.excludedSets > 0) Text(pluginI18n.text("workouts.radar.planExcluded", "count" to plan.excludedSets), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (metric == MuscleMetric.VOLUME && plan.missingVolumeSets > 0) Text(pluginI18n.text("workouts.radar.planMissing", "count" to plan.missingVolumeSets), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun MuscleRadar(balance: MuscleBalance, metric: MuscleMetric, currentLabel: String, comparisonLabel: String) {
  val labels = MuscleRegion.entries.map { pluginI18n.text("workouts.radar.region.${it.name}") }
  val primary = MaterialTheme.colorScheme.primary
  val tertiary = MaterialTheme.colorScheme.tertiary
  val grid = MaterialTheme.colorScheme.outlineVariant
  val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
  val textMeasurer = rememberTextMeasurer()
  val maximum = (balance.current + balance.previous).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
  val current = balance.current.map { animateFloatAsState((it / maximum).toFloat(), label = "muscle-current").value }
  val previous = balance.previous.map { animateFloatAsState((it / maximum).toFloat(), label = "muscle-previous").value }
  fun value(value: Double) = if (metric == MuscleMetric.SETS) value.toInt().toString() else formatKg(value)
  val text = labels.indices.map { "${labels[it]}\n${value(balance.current[it])} / ${value(balance.previous[it])}" }
  val description = currentLabel + " / " + comparisonLabel + ": " + text.joinToString("; ") { it.replace('\n', ' ') }
  Canvas(Modifier.fillMaxWidth().height(310.dp).semantics { contentDescription = description }) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = min(size.width / 2 - 36.dp.toPx(), size.height / 2 - 40.dp.toPx()).coerceAtLeast(1f)
    fun point(index: Int, fraction: Float): Offset {
      val angle = (-PI / 2 + index * 2 * PI / labels.size)
      return center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (radius * fraction)
    }
    fun polygon(values: List<Float>) = Path().apply {
      values.forEachIndexed { index, value -> val p = point(index, value); if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
      close()
    }
    for (level in 1..4) drawPath(polygon(List(labels.size) { level / 4f }), grid.copy(alpha = 0.65f), style = Stroke(1.dp.toPx()))
    labels.indices.forEach { drawLine(grid.copy(alpha = 0.5f), center, point(it, 1f), 1.dp.toPx()) }
    drawPath(polygon(previous), tertiary.copy(alpha = 0.08f))
    drawPath(polygon(previous), tertiary, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))))
    drawPath(polygon(current), primary.copy(alpha = 0.22f))
    drawPath(polygon(current), primary, style = Stroke(3.dp.toPx()))
    current.forEachIndexed { index, value -> drawCircle(primary, 3.dp.toPx(), point(index, value)) }
    text.forEachIndexed { index, label ->
      val layout = textMeasurer.measure(label, style)
      val anchor = point(index, 1f + 22.dp.toPx() / radius)
      drawText(layout, topLeft = Offset((anchor.x - layout.size.width / 2).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)),
        (anchor.y - layout.size.height / 2).coerceIn(0f, (size.height - layout.size.height).coerceAtLeast(0f))))
    }
  }
}
