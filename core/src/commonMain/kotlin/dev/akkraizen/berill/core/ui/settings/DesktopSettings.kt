package dev.akkraizen.berill.core.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.core.platform.DesktopIntegration

@Composable
internal fun DesktopSettings(desktop: DesktopIntegration) {
  var autostart by remember { mutableStateOf(desktop.isAutostartEnabled()) }
  var autostartError by remember { mutableStateOf(false) }
  var hotkeyEnabled by remember { mutableStateOf(desktop.isHotkeyEnabled()) }
  var combo by remember { mutableStateOf(desktop.hotkey()) }
  var hotkeyActive by remember { mutableStateOf(desktop.isHotkeyActive()) }

  fun applyHotkey(enabled: Boolean, newCombo: String) {
    desktop.setHotkey(enabled, newCombo)
    hotkeyEnabled = desktop.isHotkeyEnabled()
    combo = desktop.hotkey()
    hotkeyActive = desktop.isHotkeyActive()
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SettingSwitch(
      DesktopText.autostart(),
      when {
        !desktop.autostartAvailable -> DesktopText.autostartUnavailable()
        autostartError -> DesktopText.autostartFailed()
        else -> DesktopText.autostartHint()
      },
      autostart
    ) { enable ->
      autostartError = !desktop.setAutostart(enable)
      autostart = desktop.isAutostartEnabled()
    }
    SettingSwitch(
      DesktopText.hotkey(combo)(),
      if (hotkeyEnabled && !hotkeyActive) DesktopText.hotkeyTaken() else DesktopText.hotkeyHint(),
      hotkeyEnabled
    ) { enable -> applyHotkey(enable, combo) }
    AnimatedVisibility(hotkeyEnabled) {
      ChoiceChips(desktop.hotkeyOptions, combo, { it }, { applyHotkey(true, it) })
    }
  }
}

internal object DesktopText {
  val desktopTitle = Tr("core.desktop.title")
  val autostart = Tr("core.desktop.autostart")
  val autostartHint = Tr("core.desktop.autostartHint")
  val autostartUnavailable = Tr("core.desktop.autostartUnavailable")
  val autostartFailed = Tr("core.desktop.autostartFailed")
  fun hotkey(key: String) = Tr("core.desktop.hotkey", "key" to key)
  val hotkeyHint = Tr("core.desktop.hotkeyHint")
  val hotkeyTaken = Tr("core.desktop.hotkeyTaken")
}
