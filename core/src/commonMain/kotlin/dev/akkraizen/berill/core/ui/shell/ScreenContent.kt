package dev.akkraizen.berill.core.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.nav.Destination
import dev.akkraizen.berill.core.plugin.PluginScope
import dev.akkraizen.berill.core.plugin.RouteScopedContext
import dev.akkraizen.berill.core.plugin.resolvePage
import dev.akkraizen.berill.core.ui.CoreText
import dev.akkraizen.berill.core.ui.home.HomeScreen
import dev.akkraizen.berill.core.ui.notes.NotesScreen
import dev.akkraizen.berill.core.ui.settings.PluginSettingsScreen
import dev.akkraizen.berill.core.ui.settings.SettingsPage
import dev.akkraizen.berill.core.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
private fun ContentHeader(title: String, icon: ImageVector?, showBack: Boolean, onBack: () -> Unit) {
  Row(
    Modifier.fillMaxWidth()
      .padding(start = if (showBack) 8.dp else 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    if (showBack)
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, CoreText.back()) }
    if (icon != null)
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text(title, style = MaterialTheme.typography.titleLarge)
  }
}

private fun screenKey(d: Destination): String =
  when (d) {
    Destination.Home -> "home"
    Destination.Notes,
    is Destination.Note -> "notes"

    is Destination.Plugin -> "plugin:${d.id}:${d.page.orEmpty()}"
    Destination.Settings -> "settings"
    is Destination.SettingsSection -> "settings:${d.id}"
    is Destination.PluginSettings -> "plugin-settings:${d.id}"
  }

@Composable
internal fun Content(
  core: BerillCore,
  stack: List<Destination>,
  plugins: List<BerillPlugin>,
  showHeader: Boolean,
  modifier: Modifier
) {
  AnimatedContent(
    targetState = stack,
    modifier = modifier.fillMaxSize(),
    contentKey = { screenKey(it.last()) },
    transitionSpec = {
      val topLevel = targetState.size <= 2 && initialState.size <= 2
      if (topLevel)
        BerillMotion.fadeThrough()
      else
        BerillMotion.slide(forward = targetState.size > initialState.size)
    },
    label = "screen"
  ) { targetStack ->
    val current = targetStack.last()
    Column(Modifier.fillMaxSize()) {
      if (showHeader && (current is Destination.Plugin || current is Destination.PluginSettings)) {
        val id = (current as? Destination.Plugin)?.id ?: (current as Destination.PluginSettings).id
        val showBack = current is Destination.PluginSettings ||
          (current is Destination.Plugin && (current.route != null || targetStack.size > 2))
        ContentHeader(titleOf(current, core.plugins.all), core.plugins.byId(id)?.icon, showBack) {
          core.nav.back()
        }
      }
      if (showHeader && current is Destination.SettingsSection) {
        val page = SettingsPage.byId(current.id)
        ContentHeader(page?.title ?: CoreText.settings(), page?.icon, showBack = true) { core.nav.back() }
      }
      ScreenBody(core, current, plugins, Modifier.weight(1f))
    }
  }
}

@Composable
private fun ScreenBody(core: BerillCore, current: Destination, plugins: List<BerillPlugin>, modifier: Modifier) {
  BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val twoPane = maxWidth >= 900.dp
    Box(Modifier.widthIn(max = 1200.dp).fillMaxSize()) {
      when (current) {
        Destination.Home -> HomeScreen(core)

        Destination.Notes,
        is Destination.Note ->
          NotesScreen(core, openedId = (current as? Destination.Note)?.id, twoPane = twoPane)

        is Destination.Plugin -> {
          val plugin = plugins.firstOrNull { it.id == current.id }
          if (plugin == null) {
            Text(
              CoreText.sectionUnavailable(),
              modifier = Modifier.padding(16.dp),
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            val pageId = resolvePage(plugin, current.page)
            key(plugin.id, pageId) {
              val route = remember { MutableStateFlow(current.route) }
              SideEffect { route.value = current.route }
              val page = remember { MutableStateFlow(pageId) }
              val ctx = remember { RouteScopedContext(core.plugins.context(plugin), route, page) }
              val pluginPage = plugin.pages.firstOrNull { it.id == pageId }
              PluginScope(plugin) {
                if (pluginPage == null) plugin.Screen(ctx) else pluginPage.content(ctx)
              }
            }
          }
        }

        Destination.Settings -> SettingsScreen(core)
        is Destination.SettingsSection -> SettingsScreen(core, current.id)
        is Destination.PluginSettings -> PluginSettingsScreen(core, current.id)
      }
    }
  }
}
