package dev.akkraizen.berill.api.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun FieldWithActions(
  field: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  minFieldWidth: Dp = 200.dp,
  spacing: Dp = 8.dp,
  actions: @Composable FlowRowScope.() -> Unit
) {
  Layout(
    contents = listOf(
      field,
      {
        FlowRow(
          horizontalArrangement = remember(spacing) { SpreadArrangement(spacing) },
          verticalArrangement = Arrangement.spacedBy(spacing),
          itemVerticalAlignment = Alignment.CenterVertically,
          content = actions
        )
      }
    ),
    modifier = modifier
  ) { (fieldMeasurables, actionMeasurables), constraints ->
    val gap = spacing.roundToPx()
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val fieldMeasurable = fieldMeasurables.first()
    val actionMeasurable = actionMeasurables.firstOrNull()
    val actionsWidth = actionMeasurable?.maxIntrinsicWidth(Constraints.Infinity)?.coerceAtMost(width) ?: 0
    val inline = actionMeasurable == null || width - actionsWidth - gap >= minFieldWidth.roundToPx()
    if (inline) {
      val fieldWidth = if (actionMeasurable == null) width else width - actionsWidth - gap
      val fieldPlaceable = fieldMeasurable.measure(Constraints.fixedWidth(fieldWidth.coerceAtLeast(0)))
      val actionsPlaceable = actionMeasurable?.measure(Constraints.fixedWidth(actionsWidth))
      val height = maxOf(fieldPlaceable.height, actionsPlaceable?.height ?: 0)
      layout(width, height) {
        fieldPlaceable.place(0, (height - fieldPlaceable.height) / 2)
        actionsPlaceable?.place(width - actionsWidth, (height - actionsPlaceable.height) / 2)
      }
    } else {
      val fieldPlaceable = fieldMeasurable.measure(Constraints.fixedWidth(width))
      val actionsPlaceable = actionMeasurable.measure(Constraints.fixedWidth(width))
      val height = fieldPlaceable.height + gap + actionsPlaceable.height
      layout(width, height) {
        fieldPlaceable.place(0, 0)
        actionsPlaceable.place(0, fieldPlaceable.height + gap)
      }
    }
  }
}

private class SpreadArrangement(override val spacing: Dp) : Arrangement.Horizontal {
  override fun Density.arrange(
    totalSize: Int,
    sizes: IntArray,
    layoutDirection: LayoutDirection,
    outPositions: IntArray
  ) {
    if (sizes.isEmpty()) return
    val gap = spacing.roundToPx()
    val used = sizes.sum()
    val between = if (sizes.size > 1) maxOf(gap, (totalSize - used) / (sizes.size - 1)) else 0
    var x = if (sizes.size == 1) totalSize - used else 0
    sizes.forEachIndexed { i, size ->
      outPositions[i] = if (layoutDirection == LayoutDirection.Rtl) totalSize - x - size else x
      x += size + between
    }
  }
}

@Composable
fun FieldsRow(
  modifier: Modifier = Modifier,
  minItemWidth: Dp = 120.dp,
  weights: List<Float> = emptyList(),
  spacing: Dp = 8.dp,
  content: @Composable () -> Unit
) {
  Layout(content, modifier) { measurables, constraints ->
    val gap = spacing.roundToPx()
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val count = measurables.size
    if (count == 0) return@Layout layout(width, 0) {}
    val itemWeights = List(count) { weights.getOrNull(it) ?: 1f }
    val available = (width - gap * (count - 1)).coerceAtLeast(0)
    val total = itemWeights.sum()
    val widths = itemWeights.map { (available * it / total).toInt() }
    if (widths.min() >= minItemWidth.roundToPx()) {
      val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(widths[i])) }
      val height = placeables.maxOf { it.height }
      layout(width, height) {
        var x = 0
        placeables.forEach {
          it.place(x, 0)
          x += it.width + gap
        }
      }
    } else {
      val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
      val height = placeables.sumOf { it.height } + gap * (count - 1)
      layout(width, height) {
        var y = 0
        placeables.forEach {
          it.place(0, y)
          y += it.height + gap
        }
      }
    }
  }
}
