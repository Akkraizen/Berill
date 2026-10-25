package dev.akkraizen.berill.desktop.ui.window

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import kotlin.math.abs

internal fun Modifier.windowDrag(window: Window, state: WindowState, onDoubleClick: () -> Unit): Modifier =
  pointerInput(window) {
    var lastDownAt = 0L
    awaitEachGesture {
      val down = awaitFirstDown()
      if (down.uptimeMillis - lastDownAt < 400) {
        lastDownAt = 0L
        onDoubleClick()
        return@awaitEachGesture
      }
      lastDownAt = down.uptimeMillis
      var startMouse: Point = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
      var startWindow: Point = window.location
      var dragging = false
      do {
        val event = awaitPointerEvent()
        val mouse = MouseInfo.getPointerInfo()?.location ?: break
        if (!dragging && abs(mouse.x - startMouse.x) + abs(mouse.y - startMouse.y) > 4) {
          dragging = true
          if (state.placement == WindowPlacement.Maximized) {
            state.placement = WindowPlacement.Floating
            val width = state.size.width.value.toInt().takeIf { it > 0 } ?: window.width
            startWindow = Point(mouse.x - width / 2, mouse.y - 20)
            startMouse = mouse
          }
        }
        if (dragging) {
          window.setLocation(startWindow.x + mouse.x - startMouse.x, startWindow.y + mouse.y - startMouse.y)
          event.changes.forEach { it.consume() }
        }
      } while (event.changes.any { it.pressed })
    }
  }

internal fun fitMaximizedBoundsToScreen(window: Window) {
  val frame = window as? Frame ?: return
  val config = window.graphicsConfiguration ?: return
  val screen = config.bounds
  val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
  frame.maximizedBounds = Rectangle(
    insets.left,
    insets.top,
    screen.width - insets.left - insets.right,
    screen.height - insets.top - insets.bottom
  )
}

