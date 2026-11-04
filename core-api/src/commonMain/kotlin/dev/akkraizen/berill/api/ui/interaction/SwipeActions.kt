package dev.akkraizen.berill.api.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.display.Ic
import kotlin.math.abs
import kotlinx.coroutines.launch

class RowSwipeAction(
  val label: String,
  val icon: ImageVector,
  val color: Color? = null,
  val destructive: Boolean = false,
  val run: () -> Unit
)

/** Swiping a row right runs [start], swiping it left runs [end]; the action is shown behind the row while dragging. */
@Composable
fun SwipeActions(
  start: RowSwipeAction? = null,
  end: RowSwipeAction? = null,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = MaterialTheme.shapes.medium,
  surface: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  content: @Composable () -> Unit
) {
  if (!enabled || (start == null && end == null)) {
    Box(modifier) { content() }
    return
  }
  val offset = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  var width by remember { mutableIntStateOf(0) }
  val toStart by rememberUpdatedState(start)
  val toEnd by rememberUpdatedState(end)
  val shown = when {
    offset.value > 0 -> start
    offset.value < 0 -> end
    else -> null
  }
  val threshold = 96.dp
  Box(modifier.onSizeChanged { width = it.width }) {
    if (shown != null) {
      val colors = MaterialTheme.colorScheme
      val tint = shown.color ?: if (shown.destructive) colors.error else colors.primary
      val armed = abs(offset.value) >= with(androidx.compose.ui.platform.LocalDensity.current) { threshold.toPx() }
      Row(
        Modifier.matchParentSize().clip(shape).background(if (armed) tint.copy(alpha = 0.28f) else tint.copy(alpha = 0.14f)).padding(horizontal = 18.dp),
        horizontalArrangement = if (offset.value > 0) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (offset.value < 0) Text(shown.label, style = MaterialTheme.typography.labelLarge, color = tint, modifier = Modifier.padding(end = 8.dp))
        Ic(shown.icon, size = 22.dp, tint = tint)
        if (offset.value > 0) Text(shown.label, style = MaterialTheme.typography.labelLarge, color = tint, modifier = Modifier.padding(start = 8.dp))
      }
    }
    Box(
      Modifier
        .graphicsLayer { translationX = offset.value }
        .then(if (offset.value != 0f) Modifier.clip(shape).background(surface) else Modifier)
        .pointerInput(Unit) {
          val limit = threshold.toPx()
          detectHorizontalDragGestures(
            onDragEnd = {
              val value = offset.value
              val action = if (value > 0) toStart else toEnd
              scope.launch {
                if (action != null && abs(value) >= limit) {
                  if (action.destructive) offset.animateTo(if (value > 0) width.toFloat() else -width.toFloat(), BerillMotion.effects())
                  action.run()
                }
                offset.animateTo(0f, BerillMotion.spatial())
              }
            },
            onDragCancel = { scope.launch { offset.animateTo(0f, BerillMotion.spatial()) } }
          ) { change, amount ->
            val next = offset.value + amount
            val allowed = (next > 0 && toStart != null) || (next < 0 && toEnd != null) || next == 0f
            if (!allowed) return@detectHorizontalDragGestures
            change.consume()
            scope.launch { offset.snapTo(next.coerceIn(-width * 0.6f, width * 0.6f)) }
          }
        }
    ) { content() }
  }
}
