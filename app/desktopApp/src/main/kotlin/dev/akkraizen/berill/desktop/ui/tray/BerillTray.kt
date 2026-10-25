package dev.akkraizen.berill.desktop.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.isTraySupported
import dev.akkraizen.berill.core.ui.theme.Appearance
import dev.akkraizen.berill.desktop.ui.BerillIcon

@Composable
internal fun ApplicationScope.BerillTray(
  appearance: Appearance,
  onOpen: () -> Unit,
  onExit: () -> Unit
) {
  if (!isTraySupported) return
  var menuAnchor by remember { mutableStateOf<IntOffset?>(null) }
  val open = {
    menuAnchor = null
    onOpen()
  }
  val windows = remember { System.getProperty("os.name").startsWith("Windows") }
  if (windows) {
    TrayPointerEvents { menuAnchor = it }
    Tray(icon = BerillIcon, tooltip = "Berill", onAction = open)
  } else {
    Tray(icon = BerillIcon, tooltip = "Berill", onAction = open) {
      Item(TrayText.open(), onClick = open)
      Separator()
      Item(TrayText.exit(), onClick = onExit)
    }
  }
  menuAnchor?.let { anchor ->
    TrayMenuWindow(
      anchor = anchor,
      appearance = appearance,
      onDismiss = { menuAnchor = null },
      onOpen = open,
      onExit = {
        menuAnchor = null
        onExit()
      }
    )
  }
}
