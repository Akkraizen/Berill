package dev.akkraizen.berill.api.ui.interaction

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private const val SCROLL_DEAD_ZONE = 8f
private const val SCROLL_SPEED_FACTOR = 30f
private const val MAX_SCROLL_SPEED = 6000f

@Composable
fun Modifier.mouseAutoScroll(state: ScrollableState): Modifier {
  val density = LocalDensity.current.density
  val focused = LocalWindowInfo.current.isWindowFocused
  var active by remember(state) { mutableStateOf(false) }
  var direction by remember(state) { mutableStateOf(0) }
  return this
    .pointerHoverIcon(
      if (active) autoScrollCursor(direction) else PointerIcon.Default,
      overrideDescendants = active
    )
    .pointerInput(state, density, focused) {
      if (!focused) return@pointerInput
      coroutineScope {
        var scrolling: Job? = null
        var distance = 0f
        var origin = 0f
        try {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Main)
              val change = event.changes.firstOrNull() ?: continue
              val outside = change.position.x !in 0f..size.width.toFloat() ||
                change.position.y !in 0f..size.height.toFloat()
              if (outside || event.type == PointerEventType.Exit || !event.buttons.isTertiaryPressed) {
                scrolling?.cancel()
                scrolling = null
                active = false
              } else if (scrolling == null && event.type == PointerEventType.Press && !change.isConsumed) {
                origin = change.position.y
                distance = 0f
                direction = 0
                active = true
                change.consume()
                scrolling = launch {
                  state.autoScroll(density) { distance }
                }
              } else if (scrolling != null) {
                distance = change.position.y - origin
                direction = if (abs(distance) <= SCROLL_DEAD_ZONE * density) 0 else sign(distance).toInt()
                change.consume()
              }
            }
          }
        } finally {
          scrolling?.cancel()
          active = false
        }
      }
    }
}

@Composable
fun Modifier.mouseScrollable(state: ScrollState): Modifier = mouseAutoScroll(state).verticalScroll(state)

private suspend fun ScrollableState.autoScroll(density: Float, distance: () -> Float) {
  scroll(MutatePriority.UserInput) {
    var previousFrame = withFrameNanos { it }
    while (currentCoroutineContext().isActive) {
      val frame = withFrameNanos { it }
      val elapsedSeconds = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
      previousFrame = frame
      val speed = autoScrollSpeed(distance() / density)
      if (speed != 0f) scrollBy(speed * density * elapsedSeconds)
    }
  }
}

private fun autoScrollSpeed(offset: Float): Float {
  val distance = (abs(offset) - SCROLL_DEAD_ZONE).coerceAtLeast(0f)
  return (distance * SCROLL_SPEED_FACTOR).coerceAtMost(MAX_SCROLL_SPEED) * sign(offset)
}
