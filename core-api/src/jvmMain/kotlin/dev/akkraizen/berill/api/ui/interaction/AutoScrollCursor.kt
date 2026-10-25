package dev.akkraizen.berill.api.ui.interaction

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

internal actual fun autoScrollCursor(direction: Int): PointerIcon = PointerIcon(
  Cursor.getPredefinedCursor(when {
    direction < 0 -> Cursor.N_RESIZE_CURSOR
    direction > 0 -> Cursor.S_RESIZE_CURSOR
    else -> Cursor.MOVE_CURSOR
  })
)
