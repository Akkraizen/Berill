package dev.akkraizen.berill.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap

internal object WidgetBitmaps {
  fun ring(size: Int, progress: Float, color: Int, track: Int, stroke: Float): Bitmap {
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = stroke
      strokeCap = Paint.Cap.ROUND
    }
    val inset = stroke / 2 + 1
    val bounds = RectF(inset, inset, size - inset, size - inset)
    paint.color = track
    canvas.drawArc(bounds, 0f, 360f, false, paint)
    val sweep = 360f * progress.coerceIn(0f, 1f)
    if (sweep > 0f) {
      paint.color = color
      canvas.drawArc(bounds, -90f, sweep, false, paint)
    }
    return bitmap
  }

  fun sparkline(width: Int, height: Int, values: List<Double>, color: Int, stroke: Float): Bitmap {
    val bitmap = createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
    if (values.size < 2) return bitmap
    val canvas = Canvas(bitmap)
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
    val pad = stroke * 2
    val stepX = (width - pad * 2) / (values.size - 1)
    fun y(v: Double) = (height - pad - ((v - min) / span) * (height - pad * 2)).toFloat()
    val line = Path()
    values.forEachIndexed { i, v ->
      val x = pad + stepX * i
      if (i == 0) line.moveTo(x, y(v)) else line.lineTo(x, y(v))
    }
    val fill = Path(line).apply {
      lineTo(pad + stepX * (values.size - 1), height.toFloat())
      lineTo(pad, height.toFloat())
      close()
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      this.color = (color and 0x00FFFFFF) or 0x33000000
    }
    canvas.drawPath(fill, fillPaint)
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = stroke
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
      this.color = color
    }
    canvas.drawPath(line, linePaint)
    val last = values.last()
    linePaint.style = Paint.Style.FILL
    canvas.drawCircle(pad + stepX * (values.size - 1), y(last), stroke * 1.6f, linePaint)
    return bitmap
  }
}
