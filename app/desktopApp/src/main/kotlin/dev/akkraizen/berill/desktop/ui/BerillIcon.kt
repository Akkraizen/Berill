package dev.akkraizen.berill.desktop.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter

internal object BerillIcon : Painter() {
  override val intrinsicSize = Size(32f, 32f)

  private val body = Path().apply {
    moveTo(76f, 8f)
    lineTo(140f, 8f)
    cubicTo(202f, 8f, 252f, 64f, 252f, 132f)
    lineTo(252f, 255f)
    cubicTo(252f, 322f, 200f, 374f, 135f, 374f)
    lineTo(105f, 374f)
    cubicTo(100f, 374f, 96f, 371f, 95f, 366f)
    cubicTo(83f, 326f, 52f, 300f, 16f, 289f)
    cubicTo(11f, 288f, 8f, 284f, 8f, 279f)
    lineTo(8f, 72f)
    cubicTo(8f, 36f, 38f, 8f, 76f, 8f)
    close()
  }
  private val lowerFold = Path().apply {
    moveTo(105f, 228f)
    cubicTo(155f, 247f, 197f, 280f, 227f, 325f)
    cubicTo(204f, 355f, 168f, 374f, 135f, 374f)
    lineTo(96f, 374f)
    cubicTo(85f, 333f, 53f, 302f, 39f, 299f)
    cubicTo(68f, 279f, 91f, 256f, 105f, 228f)
    close()
  }
  private val innerFold = Path().apply {
    moveTo(8f, 141f)
    cubicTo(52f, 139f, 89f, 120f, 112f, 89f)
    cubicTo(138f, 162f, 121f, 244f, 39f, 299f)
    cubicTo(29f, 294f, 19f, 290f, 8f, 287f)
    close()
  }
  private val upperFold = Path().apply {
    moveTo(76f, 8f)
    lineTo(140f, 8f)
    cubicTo(140f, 82f, 81f, 141f, 8f, 141f)
    lineTo(8f, 72f)
    cubicTo(8f, 36f, 38f, 8f, 76f, 8f)
    close()
  }

  override fun DrawScope.onDraw() {
    val scale = minOf(size.width / 256f, size.height / 384f)
    withTransform({
      translate((size.width - 256f * scale) / 2f, (size.height - 384f * scale) / 2f)
      scale(scale, scale, pivot = Offset.Zero)
    }) {
      drawPath(body, Color(0xFF38A976))
      clipPath(body) {
        drawPath(lowerFold, Color(0xFF258B61))
        drawPath(innerFold, Color(0xFF187A50))
        drawPath(upperFold, Color(0xFF40CD93))
      }
    }
  }
}