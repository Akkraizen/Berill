package dev.akkraizen.berill.api.ui.markdown

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

internal fun DrawScope.drawLivePreview(result: LiveResult, layout: TextLayoutResult, theme: LiveTheme) {
  val length = layout.layoutInput.text.length
  if (length == 0) return

  fun shown(o: Int) = result.toShown(o).coerceIn(0, length)
  val pad = 3.dp.toPx()
  for (d in result.decorations) {
    when (d) {
      is LiveDecoration.Block -> {
        val top = layout.getLineTop(layout.getLineForOffset(shown(d.start))) - pad
        val bottom = layout.getLineBottom(layout.getLineForOffset(shown(d.end))) + pad
        val inset = 8.dp.toPx()
        drawRoundRect(
          d.color,
          Offset(-inset, top),
          Size(size.width + 2 * inset, bottom - top),
          CornerRadius(8.dp.toPx())
        )
      }

      is LiveDecoration.Bar -> {
        val top = layout.getLineTop(layout.getLineForOffset(shown(d.start)))
        val bottom = layout.getLineBottom(layout.getLineForOffset(shown(d.end)))
        val w = 3.dp.toPx()
        drawRoundRect(d.color, Offset(0f, top), Size(w, bottom - top), CornerRadius(w / 2))
      }

      is LiveDecoration.Rule -> {
        val line = layout.getLineForOffset(shown(d.start))
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
        drawLine(theme.rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
      }

      is LiveDecoration.Checkbox -> {
        val a = shown(d.start)
        val z = (shown(d.end) - 1).coerceAtLeast(a)
        if (a >= length) continue
        val first = layout.getBoundingBox(a)
        val last = layout.getBoundingBox(z.coerceAtMost(length - 1))
        val side = 16.dp.toPx()
        val cx = (first.left + last.right) / 2
        val cy = (first.top + first.bottom) / 2
        val topLeft = Offset(cx - side / 2, cy - side / 2)
        val radius = CornerRadius(4.dp.toPx())
        if (d.checked) {
          drawRoundRect(theme.checkFill, topLeft, Size(side, side), radius)
          val check = Path().apply {
            moveTo(topLeft.x + side * 0.24f, topLeft.y + side * 0.52f)
            lineTo(topLeft.x + side * 0.43f, topLeft.y + side * 0.70f)
            lineTo(topLeft.x + side * 0.77f, topLeft.y + side * 0.32f)
          }
          drawPath(
            check,
            theme.checkMark,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
          )
        } else {
          drawRoundRect(
            theme.checkBorder,
            topLeft,
            Size(side, side),
            radius,
            style = Stroke(width = 1.5.dp.toPx())
          )
        }
      }
    }
  }
}

internal fun hitLivePreview(result: LiveResult, layout: TextLayoutResult, x: Float, y: Float): LiveAction? {
  val length = layout.layoutInput.text.length
  for (click in result.clicks) {
    val a = result.toShown(click.start)
    val z = result.toShown(click.end).coerceAtMost(length)
    for (k in a until z) {
      val box = layout.getBoundingBox(k)
      if (x >= box.left - 2 && x <= box.right + 2 && y >= box.top && y <= box.bottom)
        return click.action
    }
  }
  return null
}
