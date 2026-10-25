package dev.akkraizen.berill.desktop.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.IntOffset
import java.awt.EventQueue
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener

@Composable
internal fun TrayPointerEvents(onContextMenu: (IntOffset) -> Unit) {
  val currentOnContextMenu by rememberUpdatedState(onContextMenu)
  DisposableEffect(Unit) {
    val systemTray = SystemTray.getSystemTray()
    var attached: TrayIcon? = null
    var disposed = false
    val mouseListener = object : MouseAdapter() {
      override fun mouseReleased(event: MouseEvent) {
        if (event.button == MouseEvent.BUTTON3 || event.isPopupTrigger) {
          currentOnContextMenu(IntOffset(event.x, event.y))
        }
      }
    }

    fun attach() {
      val icon = systemTray.trayIcons.singleOrNull { it.toolTip == "Berill" }
      if (icon === attached) return
      attached?.removeMouseListener(mouseListener)
      attached = icon
      icon?.addMouseListener(mouseListener)
    }

    fun scheduleAttach() {
      EventQueue.invokeLater { if (!disposed) attach() }
    }

    val trayListener = PropertyChangeListener { scheduleAttach() }
    systemTray.addPropertyChangeListener("trayIcons", trayListener)
    scheduleAttach()
    onDispose {
      disposed = true
      systemTray.removePropertyChangeListener("trayIcons", trayListener)
      attached?.removeMouseListener(mouseListener)
    }
  }
}
