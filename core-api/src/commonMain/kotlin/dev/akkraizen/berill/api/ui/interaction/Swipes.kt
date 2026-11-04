package dev.akkraizen.berill.api.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

enum class SwipeDirection { PREVIOUS, NEXT }

internal class SwipeTracker(private val distance: Float, private val slop: Float) {
  private var id: PointerId? = null
  private var start = Offset.Zero
  private var startTime = 0L
  var offset = 0f
    private set
  var horizontal = false
    private set
  private var cancelled = false

  fun down(pointer: PointerId, at: Offset, time: Long) {
    id = pointer
    start = at
    startTime = time
    offset = 0f
    horizontal = false
    cancelled = false
  }

  val active: Boolean get() = id != null && !cancelled

  fun owns(pointer: PointerId) = id == pointer

  fun cancel() {
    cancelled = true
    offset = 0f
  }

  fun move(at: Offset): Boolean {
    if (!active) return false
    val dx = at.x - start.x
    val dy = at.y - start.y
    if (!horizontal) {
      if (abs(dy) > slop && abs(dy) > abs(dx)) {
        cancel()
        return false
      }
      if (abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) horizontal = true
    }
    if (horizontal) offset = dx
    return horizontal
  }

  fun up(at: Offset, time: Long): SwipeDirection? {
    val wasActive = active && horizontal
    id = null
    if (!wasActive) return null
    val dx = at.x - start.x
    val dy = at.y - start.y
    val fast = time - startTime < 250 && abs(dx) > distance / 2
    if ((abs(dx) < distance && !fast) || abs(dx) < abs(dy) * 1.5f) return null
    return if (dx < 0) SwipeDirection.NEXT else SwipeDirection.PREVIOUS
  }

  fun direction(): SwipeDirection = if (offset < 0) SwipeDirection.NEXT else SwipeDirection.PREVIOUS
}

internal class ScrollSwipe(private val threshold: Float, private val window: Long = 350, private val cooldown: Long = 700) {
  private var total = 0f
  private var last = 0L
  private var blockedUntil = 0L

  val blocked: Boolean get() = last < blockedUntil

  fun reset() {
    total = 0f
  }

  fun add(delta: Float, time: Long): SwipeDirection? {
    if (time - last > window) total = 0f
    last = time
    if (time < blockedUntil) {
      blockedUntil = time + cooldown
      return null
    }
    total += delta
    if (abs(total) < threshold) return null
    val direction = if (total > 0) SwipeDirection.NEXT else SwipeDirection.PREVIOUS
    total = 0f
    blockedUntil = time + cooldown
    return direction
  }

  fun release() {
    blockedUntil = 0L
  }
}

/**
 * Horizontal swipes on touch screens and horizontal trackpad scrolling. The innermost area that accepts a
 * direction handles the gesture; horizontally scrollable children keep their own drags.
 */
@Composable
fun Modifier.swipeArea(
  canSwipe: (SwipeDirection) -> Boolean = { true },
  feedback: Boolean = true,
  onSwipe: (SwipeDirection) -> Unit
): Modifier {
  val accepts by rememberUpdatedState(canSwipe)
  val handle by rememberUpdatedState(onSwipe)
  val shift = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  return this
    .graphicsLayer { if (feedback) translationX = shift.value }
    .pointerInput(Unit) {
      val tracker = SwipeTracker(distance = 72.dp.toPx(), slop = viewConfiguration.touchSlop)
      val scroll = ScrollSwipe(threshold = 3f)
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Main)
          val change = event.changes.firstOrNull() ?: continue
          when (event.type) {
            PointerEventType.Scroll -> {
              val delta = change.scrollDelta
              if (abs(delta.x) <= abs(delta.y)) continue
              if (change.isConsumed) {
                scroll.reset()
                continue
              }
              val direction = scroll.add(delta.x, change.uptimeMillis)
              when {
                direction == null -> if (scroll.blocked) change.consume()
                accepts(direction) -> {
                  change.consume()
                  handle(direction)
                }
                else -> scroll.release()
              }
            }
            PointerEventType.Press -> {
              if ((change.type == PointerType.Touch || change.type == PointerType.Stylus) && event.changes.size == 1)
                tracker.down(change.id, change.position, change.uptimeMillis)
            }
            PointerEventType.Move -> {
              if (!tracker.active || !tracker.owns(change.id)) continue
              if (event.changes.size > 1 || change.isConsumed) {
                tracker.cancel()
                scope.launch { shift.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                continue
              }
              if (tracker.move(change.position)) {
                if (accepts(tracker.direction())) {
                  change.consume()
                  if (feedback) scope.launch { shift.snapTo((tracker.offset * 0.25f).coerceIn(-48.dp.toPx(), 48.dp.toPx())) }
                } else {
                  tracker.cancel()
                }
              }
            }
            PointerEventType.Release -> {
              if (!tracker.owns(change.id)) continue
              val direction = tracker.up(change.position, change.uptimeMillis)
              if (direction != null && accepts(direction)) {
                change.consume()
                handle(direction)
              }
              scope.launch { shift.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
            }
          }
        }
      }
    }
}
