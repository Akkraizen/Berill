package dev.akkraizen.berill.core.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.nav.Destination
import dev.akkraizen.berill.core.nav.Tab
import dev.akkraizen.berill.core.nav.tab
import dev.akkraizen.berill.core.plugin.resolvePage
import dev.akkraizen.berill.core.ui.CoreText
import dev.akkraizen.berill.core.ui.settings.SettingsPage

internal class NavEntry(
  val key: String,
  val title: String,
  val icon: ImageVector,
  val selected: Boolean,
  val onClick: () -> Unit,
  val children: List<NavEntry> = emptyList(),
  val open: Boolean = false,
  val expanded: Boolean = false,
  val onToggle: () -> Unit = {}
)

internal class NavModel(val top: List<NavEntry>, val sections: List<NavEntry>, val bottom: List<NavEntry>)

internal fun navModel(
  core: BerillCore,
  current: Destination,
  plugins: List<BerillPlugin>,
  expandedSections: Set<String> = emptySet(),
  toggleSection: (String) -> Unit = {}
): NavModel {
  fun tab(t: Tab) =
    NavEntry(
      "tab:${t.name}",
      t.title,
      t.icon,
      current.tab == t && current !is Destination.PluginSettings,
      onClick = { core.nav.selectTab(t) }
    )
  return NavModel(
    top = listOf(tab(Tab.Home), tab(Tab.Notes)),
    sections = plugins.map { p ->
      val inPlugin = current as? Destination.Plugin
      val active = inPlugin?.id == p.id || (current as? Destination.PluginSettings)?.id == p.id
      val pages = p.pages
      val page = if (inPlugin != null && inPlugin.id == p.id) resolvePage(p, inPlugin.page) else null
      val key = "plugin:${p.id}"
      NavEntry(
        key,
        p.title,
        p.icon,
        selected = active && (pages.isEmpty() || current is Destination.PluginSettings),
        onClick = { core.nav.openSection(p.id) },
        children = pages.map { pg ->
          NavEntry(
            "page:${p.id}:${pg.id}",
            pg.title(),
            pg.icon,
            selected = page == pg.id,
            onClick = { core.nav.openSection(p.id, pg.id) }
          )
        },
        open = active,
        expanded = key in expandedSections,
        onToggle = { toggleSection(key) }
      )
    },
    bottom = listOf(tab(Tab.Settings))
  )
}

internal fun titleOf(current: Destination, plugins: List<BerillPlugin>): String =
  when (current) {
    is Destination.Plugin ->
      plugins
        .firstOrNull { it.id == current.id }
        ?.let { p ->
          val page = p.pages.firstOrNull { it.id == resolvePage(p, current.page) }
          if (page == null) p.title else "${p.title} · ${page.title()}"
        } ?: CoreText.section()

    is Destination.PluginSettings ->
      CoreText.sectionSettingsTitle(plugins.firstOrNull { it.id == current.id }?.title ?: CoreText.sectionLower())()

    is Destination.SettingsSection ->
      SettingsPage.byId(current.id)?.let { "${CoreText.settings()} · ${it.title}" } ?: CoreText.settings()

    else -> current.tab?.title ?: "Berill"
  }
