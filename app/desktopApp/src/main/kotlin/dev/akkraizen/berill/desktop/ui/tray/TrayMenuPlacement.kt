package dev.akkraizen.berill.desktop.ui.tray

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

internal fun trayMenuPosition(anchor: IntOffset, size: IntSize, workArea: IntRect): IntOffset {
  val margin = 8
  val left = workArea.left + margin
  val top = workArea.top + margin
  val right = maxOf(left, workArea.right - size.width - margin)
  val bottom = maxOf(top, workArea.bottom - size.height - margin)
  return IntOffset(
    (anchor.x - size.width / 2).coerceIn(left, right),
    (anchor.y - size.height - margin).coerceIn(top, bottom)
  )
}
