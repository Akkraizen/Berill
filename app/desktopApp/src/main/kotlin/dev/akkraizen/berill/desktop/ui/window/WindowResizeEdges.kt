package dev.akkraizen.berill.desktop.ui.window

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import java.awt.Cursor
import java.awt.MouseInfo

private enum class Edge(
  val left: Boolean,
  val top: Boolean,
  val right: Boolean,
  val bottom: Boolean,
  val cursor: Int
) {
  Left(true, false, false, false, Cursor.W_RESIZE_CURSOR),
  Right(false, false, true, false, Cursor.E_RESIZE_CURSOR),
  Top(false, true, false, false, Cursor.N_RESIZE_CURSOR),
  Bottom(false, false, false, true, Cursor.S_RESIZE_CURSOR),
  TopLeft(true, true, false, false, Cursor.NW_RESIZE_CURSOR),
  TopRight(false, true, true, false, Cursor.NE_RESIZE_CURSOR),
  BottomLeft(true, false, false, true, Cursor.SW_RESIZE_CURSOR),
  BottomRight(false, false, true, true, Cursor.SE_RESIZE_CURSOR),
}

private val EDGE = 5.dp

private val CORNER = 12.dp

@Composable
fun FrameWindowScope.WindowResizeEdges() {
  Box(Modifier.fillMaxSize()) {
    EdgeHandle(
      Edge.Left,
      Modifier.align(Alignment.CenterStart)
        .width(EDGE)
        .fillMaxHeight()
        .padding(vertical = CORNER)
    )
    EdgeHandle(Edge.Right, Modifier.align(Alignment.CenterEnd).width(EDGE).fillMaxHeight().padding(vertical = CORNER))
    EdgeHandle(
      Edge.Top,
      Modifier.align(Alignment.TopCenter)
        .height(EDGE)
        .fillMaxWidth()
        .padding(horizontal = CORNER)
    )
    EdgeHandle(
      Edge.Bottom,
      Modifier.align(Alignment.BottomCenter)
        .height(EDGE)
        .fillMaxWidth()
        .padding(horizontal = CORNER)
    )
    EdgeHandle(Edge.TopLeft, Modifier.align(Alignment.TopStart).size(CORNER))
    EdgeHandle(Edge.TopRight, Modifier.align(Alignment.TopEnd).size(CORNER))
    EdgeHandle(Edge.BottomLeft, Modifier.align(Alignment.BottomStart).size(CORNER))
    EdgeHandle(Edge.BottomRight, Modifier.align(Alignment.BottomEnd).size(CORNER))
  }
}

@Composable
private fun FrameWindowScope.EdgeHandle(edge: Edge, modifier: Modifier) {
  Spacer(
    modifier.pointerHoverIcon(PointerIcon(Cursor(edge.cursor))).pointerInput(edge) {
      awaitEachGesture {
        awaitFirstDown()
        val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
        val bounds = window.bounds
        val min = window.minimumSize
        do {
          val event = awaitPointerEvent()
          val mouse = MouseInfo.getPointerInfo()?.location ?: break
          val dx = mouse.x - start.x
          val dy = mouse.y - start.y
          var x = bounds.x
          var y = bounds.y
          var w = bounds.width
          var h = bounds.height
          if (edge.left) {
            w = (bounds.width - dx).coerceAtLeast(min.width)
            x = bounds.x + bounds.width - w
          }
          if (edge.right) w = (bounds.width + dx).coerceAtLeast(min.width)
          if (edge.top) {
            h = (bounds.height - dy).coerceAtLeast(min.height)
            y = bounds.y + bounds.height - h
          }
          if (edge.bottom) h = (bounds.height + dy).coerceAtLeast(min.height)
          window.setBounds(x, y, w, h)
          event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
      }
    }
  )
}
