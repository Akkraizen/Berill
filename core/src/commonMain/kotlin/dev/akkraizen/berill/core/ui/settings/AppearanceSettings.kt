package dev.akkraizen.berill.core.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.AppLanguage
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.ui.CoreText
import dev.akkraizen.berill.core.ui.theme.ACCENT_PRESETS
import dev.akkraizen.berill.core.ui.theme.Appearance
import dev.akkraizen.berill.core.ui.theme.ThemeMode
import dev.akkraizen.berill.core.ui.theme.accentSwatch
import dev.akkraizen.berill.core.ui.theme.parseHexColor
import dev.akkraizen.berill.core.ui.theme.supportsDynamicColor

@Composable
internal fun AppearanceSettings(core: BerillCore) {
  val appearance by core.appearance.state.collectAsState()

  Text(
    CoreText.theme(),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
  ChoiceChips(
    ThemeMode.entries,
    appearance.mode,
    { it.title },
    core.appearance::setMode,
    icon = {
      when (it) {
        ThemeMode.System -> Icons.Outlined.BrightnessAuto
        ThemeMode.Light -> Icons.Outlined.LightMode
        ThemeMode.Dark -> Icons.Outlined.DarkMode
      }
    }
  )

  Text(
    CoreText.interfaceLanguage(),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
  LanguageChips(core, appearance, CoreText.languageSystem())

  Text(
    CoreText.accentColor(),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    ACCENT_PRESETS.forEach { (name, accent) ->
      AccentSwatch(
        name(),
        accentSwatch(accent),
        appearance.accent.equals(accent, ignoreCase = true)
      ) {
        core.appearance.setAccent(accent)
      }
    }
    if (supportsDynamicColor) {
      AccentSwatch(
        CoreText.accentWallpaper(),
        null,
        appearance.accent == Appearance.ACCENT_DYNAMIC
      ) {
        core.appearance.setAccent(Appearance.ACCENT_DYNAMIC)
      }
    }
  }

  val isCustom = parseHexColor(appearance.accent) != null &&
    ACCENT_PRESETS.none { it.second.equals(appearance.accent, ignoreCase = true) }
  var hex by
  remember(appearance.accent) { mutableStateOf(if (isCustom) appearance.accent else "#") }
  val parsed = parseHexColor(hex)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
      Surface(
        shape = CircleShape,
        color = parsed ?: MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(32.dp)
      ) {}
    }
    BerillTextField(
      hex,
      { hex = it.take(7) },
      label = { Text(CoreText.customColorLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
      singleLine = true,
      isError = hex.length > 1 && parsed == null,
      modifier = Modifier.weight(1f)
    )
    TextButton(
      enabled = parsed != null,
      onClick = {
        val normalized = "#" + hex.trim().removePrefix("#").uppercase()
        core.appearance.setAccent(normalized)
        hex = normalized
      }
    ) {
      Text(CoreText.applyColor())
    }
  }
  if (isCustom)
    Text(
      CoreText.customColorSelected(appearance.accent)(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AccentSwatch(name: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
  Column(Modifier.widthIn(min = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Surface(
      onClick = onClick,
      shape = CircleShape,
      color = color ?: MaterialTheme.colorScheme.surfaceContainerHighest,
      border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
      modifier = Modifier.size(40.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        when {
          selected ->
            Ic(
              BerillIcons.Check,
              tint = if (color != null && color.luminance() < 0.5f) Color.White else Color.Black
            )

          color == null ->
            Ic(Icons.Outlined.Wallpaper, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    Text(
      name,
      style = MaterialTheme.typography.labelSmall,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 4.dp)
    )
  }
}

@Composable
internal fun LanguageChips(core: BerillCore, appearance: Appearance, systemLabel: String) {
  ChoiceChips(
    listOf(Appearance.LANGUAGE_SYSTEM) + AppLanguage.entries.map { it.code },
    appearance.language,
    { code -> if (code == Appearance.LANGUAGE_SYSTEM) systemLabel else AppLanguage.fromCode(code)?.nativeName ?: code },
    core.appearance::setLanguage,
    icon = { code -> if (code == Appearance.LANGUAGE_SYSTEM) Icons.Outlined.Language else Icons.Outlined.Translate }
  )
}
