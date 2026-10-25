package dev.akkraizen.berill.api.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import kotlin.math.abs
import kotlinx.datetime.LocalDate

@Composable
fun StatisticsPeriod(days: Int, onChange: (Int) -> Unit) {
  ChoiceChips(listOf(7, 30, 90), days, { I18n.plural("charts.days", it, "count" to it) }, onChange)
}

@Composable
fun StatisticsChart(
  labels: List<String>,
  series: List<ChartSeries>,
  modifier: Modifier = Modifier,
  kind: ChartKind = ChartKind.Bars,
  target: Double? = null,
  positions: List<Double>? = null,
  valueLabel: (Double) -> String = ::chartNumber
) {
  if (labels.isEmpty() || series.none { line -> line.values.any { it?.isFinite() == true } }) {
    EmptyHint(I18n.text("charts.empty"))
    return
  }
  require(series.all { it.values.size == labels.size })
  val colors = MaterialTheme.colorScheme
  val palette = listOf(colors.primary, colors.tertiary, colors.secondary, colors.error)
  val bounds = remember(series, target, kind) {
    chartBounds(series.flatMap { it.values }, target, includeZero = kind == ChartKind.Bars)
  }
  val xValues =
    positions?.takeIf { it.size == labels.size && it.all(Double::isFinite) } ?: labels.indices.map(
      Int::toDouble
    )
  val xLow = xValues.min()
  val xSpan = (xValues.max() - xLow).coerceAtLeast(1.0)
  var selected by remember(labels, series) {
    mutableIntStateOf(labels.indices.lastOrNull { i -> series.any { it.values[i]?.isFinite() == true } }
      ?: labels.lastIndex)
  }
  var table by rememberSaveable { mutableStateOf(false) }
  val reveal = remember { Animatable(0f) }
  LaunchedEffect(labels, series) {
    reveal.snapTo(0f); reveal.animateTo(
    1f,
    BerillMotion.slowSpatial()
  )
  }
  val description = I18n.text("charts.description")
  val goal = target?.takeIf { it.isFinite() }

  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      series.forEachIndexed { i, line ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(Modifier.size(8.dp).clip(CircleShape).background(palette[i % palette.size]))
          Text(line.label, style = MaterialTheme.typography.labelMedium)
        }
      }
      if (goal != null) Text(
        "${I18n.text("charts.target")}: ${valueLabel(goal)}",
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant
      )
    }
    Row(Modifier.fillMaxWidth()) {
      Column(
        Modifier.width(64.dp).height(180.dp).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          valueLabel(bounds.high),
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          valueLabel(bounds.low),
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Canvas(
        Modifier.weight(1f).height(180.dp).semantics { contentDescription = description }
          .pointerInput(labels, series, kind, xValues) {
            detectTapGestures { point ->
              selected = if (kind == ChartKind.Bars) {
                (point.x / size.width.coerceAtLeast(1) * labels.size).toInt()
                  .coerceIn(labels.indices)
              } else {
                val fraction =
                  ((point.x - 8.dp.toPx()) / (size.width - 16.dp.toPx()).coerceAtLeast(1f)).coerceIn(
                    0f,
                    1f
                  )
                labels.indices.minBy { abs((xValues[it] - xLow) / xSpan - fraction) }
              }
            }
          }
      ) {
        val inset = 8.dp.toPx()
        val height = size.height - inset * 2
        fun y(value: Double) = inset + (1 - bounds.fraction(value)) * height
        fun x(index: Int) = if (kind == ChartKind.Bars) (index + 0.5f) * size.width / labels.size
        else if (labels.size == 1) size.width / 2 else inset + ((xValues[index] - xLow) / xSpan * (size.width - inset * 2)).toFloat()

        val baseline = y(0.0)
        repeat(4) { i ->
          val gridY = inset + height * i / 3
          drawLine(
            colors.outlineVariant,
            Offset(0f, gridY),
            Offset(size.width, gridY),
            1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
          )
        }
        drawLine(
          colors.outlineVariant,
          Offset(x(selected), inset),
          Offset(x(selected), size.height - inset),
          1.dp.toPx()
        )
        series.forEachIndexed { lineIndex, line ->
          val color = palette[lineIndex % palette.size]
          if (kind == ChartKind.Bars) {
            val slot = size.width / labels.size
            val barWidth = (slot * 0.72f / series.size).coerceAtMost(28.dp.toPx())
            line.values.forEachIndexed point@{ i, value ->
              if (value == null || !value.isFinite()) return@point
              val top = baseline + (y(value) - baseline) * reveal.value
              val left = x(i) + (lineIndex - (series.size - 1) / 2f) * barWidth - barWidth * 0.42f
              val h = abs(top - baseline)
              drawRoundRect(
                color.copy(alpha = if (i == selected) 1f else 0.72f),
                Offset(left, minOf(top, baseline)),
                Size(barWidth * 0.84f, h),
                CornerRadius(minOf(4.dp.toPx(), h / 2))
              )
            }
          } else {
            clipRect(right = size.width * reveal.value) {
              line.values.forEachIndexed point@{ i, value ->
                if (value == null || !value.isFinite()) return@point
                val previous = line.values.getOrNull(i - 1)?.takeIf { it.isFinite() }
                if (previous != null) {
                  val fill = Path().apply {
                    moveTo(x(i - 1), y(previous)); lineTo(x(i), y(value))
                    lineTo(x(i), size.height - inset); lineTo(
                    x(i - 1),
                    size.height - inset
                  ); close()
                  }
                  drawPath(
                    fill,
                    Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent))
                  )
                  drawLine(
                    color,
                    Offset(x(i - 1), y(previous)),
                    Offset(x(i), y(value)),
                    3.dp.toPx(),
                    StrokeCap.Round
                  )
                }
                if (labels.size <= 31 || i == selected) {
                  drawCircle(
                    color,
                    if (i == selected) 5.dp.toPx() else 3.dp.toPx(),
                    Offset(x(i), y(value))
                  )
                }
              }
            }
          }
        }
        if (goal != null) drawLine(
          colors.onSurfaceVariant,
          Offset(0f, y(goal)),
          Offset(size.width, y(goal)),
          1.5.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        )
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(start = 64.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        labels.first(),
        Modifier.weight(1f),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      if (labels.size > 1) Text(
        labels.last(),
        Modifier.weight(1f),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
      Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { selected-- }, enabled = selected > 0) {
          Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, I18n.text("charts.previous"))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(labels[selected], style = MaterialTheme.typography.labelLarge)
          series.forEachIndexed { i, line ->
            val value = line.values[selected]?.takeIf { it.isFinite() }
            Text(
              "${line.label}: ${value?.let(valueLabel) ?: I18n.text("charts.missing")}",
              style = MaterialTheme.typography.bodySmall,
              color = palette[i % palette.size]
            )
          }
        }
        IconButton(onClick = { selected++ }, enabled = selected < labels.lastIndex) {
          Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, I18n.text("charts.next"))
        }
      }
    }
    TextButton(onClick = {
      table = !table
    }) { Text(I18n.text(if (table) "charts.hideTable" else "charts.table")) }
    if (table) labels.forEachIndexed { index, label ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Column(horizontalAlignment = Alignment.End) {
          series.forEach { line ->
            Text(
              "${line.label}: ${
                line.values[index]?.takeIf { it.isFinite() }
                  ?.let(valueLabel) ?: I18n.text("charts.missing")
              }",
              style = MaterialTheme.typography.bodySmall
            )
          }
        }
      }
    }
  }
}

@Composable
fun CategoryChart(
  values: List<Pair<String, Double>>,
  valueLabel: (Double) -> String = ::chartNumber
) {
  val valid = values.filter { it.second.isFinite() && it.second >= 0 }
  if (valid.none { it.second > 0 }) {
    EmptyHint(I18n.text("charts.empty")); return
  }
  val max = valid.maxOf { it.second }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    valid.forEach { (label, value) ->
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          Text(valueLabel(value), style = MaterialTheme.typography.labelLarge)
        }
        ProgressLine((value / max).toFloat())
      }
    }
  }
}

@Composable
fun MonthlyActivityChart(dates: List<LocalDate>, label: String) {
  val values = monthlyActivity(dates)
  StatisticsChart(
    values.map { it.first.title },
    listOf(ChartSeries(label, values.map { it.second }))
  )
}
