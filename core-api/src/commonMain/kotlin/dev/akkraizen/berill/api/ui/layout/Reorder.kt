package dev.akkraizen.berill.api.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import dev.akkraizen.berill.api.ui.interaction.dragAnywhere

class ReorderState {
  var dragging by mutableStateOf<String?>(null)
  var pointer by mutableStateOf(Offset.Zero)
  var grab by mutableStateOf(Offset.Zero)
  var settled: String? = null
  var target by mutableStateOf<String?>(null)
  val bounds = mutableStateMapOf<String, Rect>()
}

@Composable
fun Modifier.reorderable(state: ReorderState, key: String, order: () -> List<String>, live: Boolean = true, onOrder: (List<String>) -> Unit): Modifier {
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
      } else if (state.target == key) {
        scaleX = 0.96f
        scaleY = 0.96f
        alpha = 0.55f
      }
    }
    .dragAnywhere(
      key,
      onStart = { at ->
        state.dragging = key
        state.settled = null
        state.grab = at
        state.pointer = (state.bounds[key]?.topLeft ?: Offset.Zero) + at
      },
      onMove = { delta ->
        state.pointer += delta
        if (live) {
          val current = order()
          state.settled?.let { last -> if (state.bounds[last]?.contains(state.pointer) != true) state.settled = null }
          val target = state.bounds.entries.firstOrNull { (k, rect) ->
            k != key && k != state.settled && k in current && rect.deflate(minOf(rect.width, rect.height) * 0.2f).contains(state.pointer)
          }?.key
          if (target != null) {
            state.settled = target
            val next = current.toMutableList().apply { remove(key); add(current.indexOf(target), key) }
            if (next != current) onOrder(next)
          }
        } else {
          state.target = dropTarget(state, key, order())
        }
      },
      onEnd = {
        if (!live) {
          val current = order()
          val next = dropOrder(state, key, current)
          if (next != current) onOrder(next)
        }
        state.dragging = null
        state.settled = null
        state.target = null
      },
      onCancel = {
        state.dragging = null
        state.target = null
      }
    )
}

fun <T> applyManualOrder(items: List<T>, order: List<String>, id: (T) -> String): List<T> {
  if (order.isEmpty()) return items
  val rank = order.withIndex().associate { it.value to it.index }
  return items.withIndex().sortedWith(compareBy({ rank[id(it.value)] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
}

fun mergeManualOrder(previous: List<String>, visible: List<String>): List<String> {
  val shown = visible.toSet()
  val slots = previous.withIndex().filter { it.value in shown }.map { it.index }
  if (slots.size != visible.size) return visible + previous.filter { it !in shown }
  val result = previous.toMutableList()
  slots.zip(visible).forEach { (slot, id) -> result[slot] = id }
  return result
}

internal fun dropTarget(state: ReorderState, key: String, order: List<String>): String? =
  order.firstOrNull { k -> k != key && state.bounds[k]?.contains(state.pointer) == true }

/** The order after [key] is dropped at the pointer: into the card under it, otherwise next to the nearest card. */
internal fun dropOrder(state: ReorderState, key: String, order: List<String>): List<String> {
  val pointer = state.pointer
  val rest = order.filter { it != key }
  val others = rest.mapNotNull { k -> state.bounds[k]?.let { k to it } }
  if (others.isEmpty() || key !in order) return order
  others.firstOrNull { it.second.contains(pointer) }?.let { (target, _) ->
    return order.toMutableList().apply { remove(key); add(order.indexOf(target), key) }
  }
  if (state.bounds[key]?.contains(pointer) == true) return order
  val (nearest, rect) = others.minBy { (_, r) -> (r.center - pointer).getDistanceSquared() }
  val after = pointer.y > rect.bottom || (pointer.y >= rect.top && pointer.x > rect.center.x)
  val index = rest.indexOf(nearest) + if (after) 1 else 0
  return rest.toMutableList().apply { add(index.coerceIn(0, size), key) }
}
