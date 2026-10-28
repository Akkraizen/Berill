package dev.akkraizen.berill.core.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.external.ExternalPluginsSettings
import dev.akkraizen.berill.core.external.ExternalText
import dev.akkraizen.berill.core.ui.CoreText
import dev.akkraizen.berill.core.ui.onboarding.OnboardingText

internal enum class SettingsPage(val icon: ImageVector, private val tr: Tr, private val hintTr: Tr) {
  Appearance(Icons.Outlined.Palette, CoreText.appearance, Tr("core.settings.appearanceHint")),
  Sync(Icons.Outlined.Sync, CoreText.sync, Tr("core.settings.syncHint")),
  Sections(Icons.Outlined.Extension, CoreText.sections, Tr("core.settings.sectionsHint")),
  External(Icons.Outlined.Shield, ExternalText.sectionTitle, Tr("core.settings.externalHint")),
  Data(Icons.Outlined.Storage, CoreText.dataOnDevice, Tr("core.settings.dataHint")),
  Desktop(Icons.Outlined.Computer, DesktopText.desktopTitle, Tr("core.settings.desktopHint")),
  Assistants(Icons.Outlined.SmartToy, McpText.title, Tr("core.settings.mcpHint")),
  About(Icons.Outlined.Info, DiagText.about, Tr("core.settings.aboutHint"));

  val id: String
    get() = name.lowercase()

  val title: String
    get() = tr()

  val hint: String
    get() = hintTr()

  companion object {
    fun byId(id: String?): SettingsPage? = entries.firstOrNull { it.id == id }

    fun available(core: BerillCore): List<SettingsPage> = entries.filter { (it != Desktop || core.desktop != null) && (it != Assistants || core.mcp != null) }
  }
}

@Composable
internal fun SettingsScreen(core: BerillCore, section: String? = null) {
  val pages = SettingsPage.available(core)
  val opened = SettingsPage.byId(section)?.takeIf { it in pages }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    if (maxWidth >= WIDE) {
      var selected by rememberSaveable { mutableStateOf((opened ?: pages.first()).id) }
      val page = SettingsPage.byId(selected)?.takeIf { it in pages } ?: pages.first()
      Row(Modifier.fillMaxSize()) {
        Column(
          Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            CoreText.settings(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp)
          )
          pages.forEach { p -> SettingsNavRow(p, selected = p == page, showChevron = false) { selected = p.id } }
        }
        VerticalDivider()
        AnimatedContent(page, Modifier.weight(1f), transitionSpec = { BerillMotion.fadeThrough() }, label = "settings") {
          SettingsPageContent(core, it, showTitle = true)
        }
      }
    } else if (opened != null) {
      SettingsPageContent(core, opened, showTitle = false)
    } else {
      PluginList {
        item { Text(CoreText.settings(), style = MaterialTheme.typography.headlineMedium) }
        item {
          SectionCard {
            pages.forEach { p -> SettingsNavRow(p, selected = false, showChevron = true) { core.nav.openSettings(p.id) } }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsNavRow(page: SettingsPage, selected: Boolean, showChevron: Boolean, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Surface(
    color = if (selected) colors.secondaryContainer else colors.surface.copy(alpha = 0f),
    contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      Modifier.fillMaxWidth().interactive(MaterialTheme.shapes.medium, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Ic(page.icon, size = 22.dp, tint = if (selected) colors.onSecondaryContainer else colors.primary)
      Column(Modifier.weight(1f)) {
        Text(page.title, style = MaterialTheme.typography.titleSmall)
        Text(
          page.hint,
          style = MaterialTheme.typography.bodySmall,
          color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
      if (showChevron) Ic(Icons.AutoMirrored.Outlined.KeyboardArrowRight, tint = colors.onSurfaceVariant)
    }
  }
}

@Composable
private fun SettingsPageContent(core: BerillCore, page: SettingsPage, showTitle: Boolean) {
  PluginList {
    if (showTitle) item { Text(page.title, style = MaterialTheme.typography.headlineSmall) }
    when (page) {
      SettingsPage.Appearance -> item { SectionCard { AppearanceSettings(core) } }
      SettingsPage.Sync -> item { SectionCard { SyncSettings(core) } }
      SettingsPage.Sections -> {
        item { SectionCard(CoreText.sections(), Icons.Outlined.Extension) { InstalledPlugins(core) } }
        item { SectionCard(CoreText.sectionCatalog(), Icons.Outlined.Apps) { AvailablePlugins(core) } }
      }
      SettingsPage.External -> item { SectionCard { ExternalPluginsSettings(core) } }
      SettingsPage.Data -> item { SectionCard { DataOverview(core) } }
      SettingsPage.Desktop -> core.desktop?.let { desktop -> item { SectionCard { DesktopSettings(desktop) } } }
      SettingsPage.Assistants -> core.mcp?.let { mcp -> item { SectionCard { McpSettings(mcp) } } }
      SettingsPage.About -> {
        item { SectionCard { AboutApp(core) } }
        item { TextButton(onClick = { core.replayOnboarding() }) { Text(OnboardingText.replay()) } }
      }
    }
  }
}

private val WIDE = 840.dp
