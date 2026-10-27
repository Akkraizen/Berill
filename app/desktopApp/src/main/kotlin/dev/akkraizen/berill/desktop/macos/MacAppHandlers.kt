package dev.akkraizen.berill.desktop.macos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import dev.akkraizen.berill.core.log.AppLog
import java.awt.Desktop
import java.awt.desktop.AppReopenedListener

@Composable
internal fun MacAppHandlers(onReopen: () -> Unit, onQuit: () -> Unit) {
  val reopen by rememberUpdatedState(onReopen)
  val quit by rememberUpdatedState(onQuit)
  DisposableEffect(Unit) {
    val desktop = runCatching { Desktop.getDesktop() }.getOrNull()
    val listener = AppReopenedListener { reopen() }
    runCatching {
      if (desktop?.isSupported(Desktop.Action.APP_EVENT_REOPENED) == true) desktop.addAppEventListener(listener)
      if (desktop?.isSupported(Desktop.Action.APP_QUIT_HANDLER) == true)
        desktop.setQuitHandler { _, response ->
          runCatching { quit() }
          response.performQuit()
        }
    }.onFailure { AppLog.warn("desktop", "macOS app handlers are unavailable", it) }
    onDispose { runCatching { desktop?.removeAppEventListener(listener) } }
  }
}
