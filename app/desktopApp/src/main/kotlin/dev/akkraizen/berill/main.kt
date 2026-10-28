package dev.akkraizen.berill

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.interaction.HotKeys
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.BuildInfo
import dev.akkraizen.berill.core.createDesktopCore
import dev.akkraizen.berill.core.external.PLUGIN_RUNNER_FLAG
import dev.akkraizen.berill.core.external.runPluginRunner
import dev.akkraizen.berill.core.files.DesktopImagePicker
import dev.akkraizen.berill.core.ui.startup.BerillStartup
import dev.akkraizen.berill.core.ui.theme.Appearance
import dev.akkraizen.berill.core.ui.theme.cachedDesktopAppearance
import dev.akkraizen.berill.desktop.windows.BACKGROUND_FLAG
import dev.akkraizen.berill.core.platform.DesktopIntegration
import dev.akkraizen.berill.desktop.DesktopOs
import dev.akkraizen.berill.desktop.SingleInstance
import dev.akkraizen.berill.desktop.createDesktopIntegration
import dev.akkraizen.berill.desktop.macos.MacAppHandlers
import dev.akkraizen.berill.desktop.ui.BerillIcon
import dev.akkraizen.berill.desktop.ui.tray.BerillTray
import dev.akkraizen.berill.desktop.ui.window.DesktopWindowEffects
import dev.akkraizen.berill.desktop.ui.window.WindowResizeEdges
import dev.akkraizen.berill.desktop.ui.window.WindowTitleBar
import dev.akkraizen.berill.desktop.ui.window.rememberPersistentWindowState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

fun main(args: Array<String>) {
  if (args.firstOrNull() == PLUGIN_RUNNER_FLAG)
    return runPluginRunner()
  val background = BACKGROUND_FLAG in args
  if (DesktopOs.isMac) System.setProperty("apple.awt.application.appearance", "system")
  val dir = File(System.getProperty("user.home"), ".berill")
  val instance = SingleInstance.acquire(dir, notify = !background) ?: return
  val desktop = createDesktopIntegration(dir, onHotkey = instance::activate)
  val cached = cachedDesktopAppearance()
  I18n.language = cached.appLanguage
  val preload =
    if (background) CoroutineScope(Dispatchers.IO).async { createDesktopCore(BerillPlugins, desktop) } else null
  app(instance, desktop, background, preload, cached)
}

private fun app(
  instance: SingleInstance,
  desktop: DesktopIntegration,
  background: Boolean,
  preload: Deferred<BerillCore>?,
  cached: Appearance
) =
  application {
    var core by remember { mutableStateOf<BerillCore?>(null) }
    val persistentWindow = rememberPersistentWindowState()
    val state = persistentWindow.state
    val trayAvailable = remember { isTraySupported }
    var windowVisible by remember { mutableStateOf(!(background && trayAvailable)) }
    val exit: () -> Unit = {
      persistentWindow.save()
      exitApplication()
    }
    val closeWindow: () -> Unit = {
      persistentWindow.save()
      if (trayAvailable)
        windowVisible = false
      else
        exit()
    }
    val restoreWindow: () -> Unit = {
      state.isMinimized = false
      windowVisible = true
    }
    if (DesktopOs.isMac) MacAppHandlers(onReopen = restoreWindow, onQuit = { persistentWindow.save() })
    val activations by instance.activations.collectAsState()
    LaunchedEffect(activations) {
      if (activations > 0)
        restoreWindow()
    }
    val appearance = core?.appearance?.state?.collectAsState()?.value ?: cached
    BerillTray(
      appearance = appearance,
      onOpen = restoreWindow,
      onExit = exit
    )
    Window(
      onCloseRequest = closeWindow,
      visible = windowVisible,
      title = "Berill ${BuildInfo.VERSION_NAME}",
      icon = BerillIcon,
      state = state,
      undecorated = !DesktopOs.isMac,
      onPreviewKeyEvent = { e ->
        when {
          e.type != KeyEventType.KeyDown -> false
          e.key == Key.Escape -> core?.onEscapeKey() ?: false
          e.key == Key.Enter || e.key == Key.NumPadEnter -> !HotKeys.isTextInputFocused
          else -> false
        }
      }
    ) {
      DesktopWindowEffects(visible = windowVisible)
      LaunchedEffect(activations) {
        if (activations > 0) {
          window.toFront()
          window.requestFocus()
        }
      }
      Box(Modifier.fillMaxSize()) {
        BerillStartup(
          initialAppearance = cached,
          load = {
            preload?.let { runCatching { it.await() }.getOrNull() }
              ?: withContext(Dispatchers.IO) { createDesktopCore(BerillPlugins, desktop) }
          },
          onReady = { core = it },
          windowChrome = { title ->
            WindowTitleBar(title = title, state = state, onClose = closeWindow)
          },
          overlay = { DesktopImagePicker.Content() }
        )
        if (!DesktopOs.isMac && state.placement == WindowPlacement.Floating)
          WindowResizeEdges()
      }
    }
  }
