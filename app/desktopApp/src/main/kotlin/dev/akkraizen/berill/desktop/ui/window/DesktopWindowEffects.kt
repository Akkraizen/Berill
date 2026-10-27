package dev.akkraizen.berill.desktop.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.FrameWindowScope
import dev.akkraizen.berill.desktop.DesktopOs
import java.awt.Dimension

@Composable
internal fun FrameWindowScope.DesktopWindowEffects(visible: Boolean) {
  LaunchedEffect(Unit) {
    window.minimumSize = Dimension(480, 360)
    window.roundCorners()
    if (DesktopOs.isMac) {
      window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
      window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
      window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
    }
  }
  LaunchedEffect(visible) {
    if (visible) {
      window.toFront()
      window.requestFocus()
    }
  }
}