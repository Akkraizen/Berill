package dev.akkraizen.berill.api.ui.interaction

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

fun Modifier.dragAnywhere(
  key: Any?,
  onStart: (Offset) -> Unit,
  onMove: (Offset) -> Unit,
  onEnd: () -> Unit,
  onCancel: () -> Unit = onEnd
): Modifier =
  pointerInput(key) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      val start =
        if (down.type == PointerType.Touch) awaitLongPressOrCancellation(down.id)
        else awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
      if (start == null) return@awaitEachGesture
      onStart(start.position)
      var finished = false
      try {
        val completed = drag(start.id) { change ->
          onMove(change.positionChange())
          change.consume()
        }
        finished = true
        if (completed) onEnd() else onCancel()
      } finally {
        if (!finished) onCancel()
      }
    }
  }
