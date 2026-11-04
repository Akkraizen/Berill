package dev.akkraizen.berill.api.ui.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.interaction.dragAnywhere
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch

/** Column of every card for [columns] columns: the stored one when known, otherwise the next column in turn. */
fun cardColumnAssignment(keys: List<String>, columns: Int, fullWidth: (String) -> Boolean, stored: Map<String, Int>): Map<String, Int> {
  val result = linkedMapOf<String, Int>()
  var run = 0
  for (key in keys) {
    if (fullWidth(key)) {
      run = 0
      continue
    }
    result[key] = stored[key]?.takeIf { it in 0 until columns } ?: (run % columns)
    run++
  }
  return result
}

/** Live drag of a card between columns, launcher style; see [CardColumns] and [columnDraggable]. */
class ColumnDragState {
  var dragging by mutableStateOf<String?>(null)
  var pointer by mutableStateOf(Offset.Zero)
  var grab = Offset.Zero
  val bounds = mutableStateMapOf<String, Rect>()
  var container = Rect.Zero
  var columns = 1
  var columnWidth = 0f
  var gap = 0f
  var order by mutableStateOf<List<String>?>(null)
  var placement by mutableStateOf<Map<String, Int>?>(null)

  fun columnAt(x: Float): Int =
    ((x - container.left) / (columnWidth + gap).coerceAtLeast(1f)).toInt().coerceIn(0, (columns - 1).coerceAtLeast(0))
}

/**
 * Cards in columns without gaps, each right under the previous card of its own column. A card keeps the column
 * given by [columnOf] (or the next one in turn when it has none); cards for which [fullWidth] is true span every
 * column and start below the tallest one.
 */
@Composable
fun CardColumns(
  keys: List<String>,
  modifier: Modifier = Modifier,
  minColumnWidth: Dp = 340.dp,
  spacing: Dp = 12.dp,
  fullWidth: (String) -> Boolean = { false },
  columnOf: (key: String, columns: Int) -> Int? = { _, _ -> null },
  drag: ColumnDragState? = null,
  card: @Composable (String) -> Unit
) {
  val shown = drag?.order?.filter { it in keys }?.let { it + keys.filter { k -> k !in it } } ?: keys
  Layout(
    modifier = modifier.onGloballyPositioned { coordinates -> drag?.container = coordinates.boundsInRoot() },
    content = {
      shown.forEach { k ->
        key(k) { Box(Modifier.layoutId(k).animatePlacement(drag?.dragging != k)) { card(k) } }
      }
    }
  ) { measurables, constraints ->
    val gap = spacing.roundToPx()
    val width = constraints.maxWidth
    val columns = ((width + gap) / (minColumnWidth.roundToPx() + gap)).coerceAtLeast(1)
    val columnWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
    drag?.let {
      it.columns = columns
      it.columnWidth = columnWidth.toFloat()
      it.gap = gap.toFloat()
    }
    val stored = drag?.placement ?: shown.mapNotNull { k -> columnOf(k, columns)?.let { k to it } }.toMap()
    val assigned = cardColumnAssignment(shown, columns, fullWidth, stored)
    val tops = IntArray(columns)
    val placed = measurables.map { measurable ->
      val key = measurable.layoutId as String
      val column = assigned[key]
      if (column == null) {
        val placeable = measurable.measure(Constraints.fixedWidth(width))
        val top = tops.max()
        tops.fill(top + placeable.height + gap)
        Triple(placeable, 0, top)
      } else {
        val placeable = measurable.measure(Constraints.fixedWidth(columnWidth))
        val top = tops[column]
        tops[column] = top + placeable.height + gap
        Triple(placeable, column * (columnWidth + gap), top)
      }
    }
    val height = if (placed.isEmpty()) 0 else (tops.max() - gap).coerceAtLeast(0)
    layout(width, height) {
      placed.forEach { (placeable, x, y) -> placeable.place(x, y) }
    }
  }
}

/**
 * Drags a card of [CardColumns]: while it moves, it joins the column under the pointer above the first card of that
 * column whose middle is below the pointer, so only that column makes room. [onDrop] receives the new order and
 * the column of every card.
 */
@Composable
fun Modifier.columnDraggable(
  state: ColumnDragState,
  key: String,
  order: () -> List<String>,
  fullWidth: (String) -> Boolean,
  columnOf: (key: String, columns: Int) -> Int?,
  onDrop: (order: List<String>, columns: Map<String, Int>, count: Int) -> Unit
): Modifier {
  val dragged = state.dragging == key
  androidx.compose.runtime.DisposableEffect(state, key) {
    onDispose { if (state.dragging == key) state.dragging = null }
  }
  return this
    .onGloballyPositioned { state.bounds[key] = it.boundsInRoot() }
    .zIndex(if (dragged) 1f else 0f)
    .graphicsLayer {
      if (dragged) {
        val origin = state.bounds[key]?.topLeft ?: Offset.Zero
        val shift = state.pointer - state.grab - origin
        translationX = shift.x
        translationY = shift.y
        scaleX = 1.02f
        scaleY = 1.02f
        shadowElevation = 16f
      }
    }
    .dragAnywhere(
      key,
      onStart = { at ->
        val current = order()
        state.order = current
        state.placement = cardColumnAssignment(current, state.columns, fullWidth, current.mapNotNull { k -> columnOf(k, state.columns)?.let { k to it } }.toMap())
        state.dragging = key
        state.grab = at
        state.pointer = (state.bounds[key]?.topLeft ?: Offset.Zero) + at
      },
      onMove = { delta ->
        state.pointer += delta
        val current = state.order ?: return@dragAnywhere
        val placement = state.placement ?: return@dragAnywhere
        val column = if (fullWidth(key)) null else state.columnAt(state.pointer.x)
        val anchors = current.filter { k -> k != key && (fullWidth(k) || column == null || placement[k] == column) }
        val before = anchors.firstOrNull { k -> state.bounds[k]?.let { it.center.y > state.pointer.y } == true }
        val rest = current.filter { it != key }
        val next = rest.toMutableList().apply { add(before?.let { rest.indexOf(it) } ?: rest.size, key) }
        val nextPlacement = if (column == null) placement else placement + (key to column)
        if (next != current) state.order = next
        if (nextPlacement != placement) state.placement = nextPlacement
      },
      onEnd = {
        val finalOrder = state.order
        val finalPlacement = state.placement
        state.dragging = null
        if (finalOrder != null && finalPlacement != null) onDrop(finalOrder, finalPlacement, state.columns)
        state.order = null
        state.placement = null
      },
      onCancel = {
        state.dragging = null
        state.order = null
        state.placement = null
      }
    )
}

/** Slides an element from its previous place to its new one when the layout moves it. */
@Composable
fun Modifier.animatePlacement(enabled: Boolean = true): Modifier {
  val scope = rememberCoroutineScope()
  val animated by rememberUpdatedState(enabled)
  var target by remember { mutableStateOf<IntOffset?>(null) }
  var motion by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
  return this
    .onPlaced { target = it.positionInParent().round() }
    .offset {
      val goal = target ?: return@offset IntOffset.Zero
      val current = motion ?: Animatable(goal, IntOffset.VectorConverter).also { motion = it }
      if (!animated) {
        if (current.targetValue != goal || current.value != goal) scope.launch { current.snapTo(goal) }
        return@offset IntOffset.Zero
      }
      if (current.targetValue != goal) scope.launch {
        current.animateTo(goal, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
      }
      current.value - goal
    }
}
