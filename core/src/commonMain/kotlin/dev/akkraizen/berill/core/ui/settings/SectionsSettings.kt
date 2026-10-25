package dev.akkraizen.berill.core.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import dev.akkraizen.berill.api.ui.interaction.mouseScrollable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.plugin.PluginScope
import dev.akkraizen.berill.core.ui.CoreText

@Composable
internal fun InstalledPlugins(core: BerillCore) {
  val installed by core.plugins.installed.collectAsState()
  val enabled by core.plugins.enabled.collectAsState()
  var removing by remember { mutableStateOf<BerillPlugin?>(null) }

  if (installed.isEmpty()) EmptyHint(CoreText.noSectionsInstalled())
  installed.forEach { p ->
    val on = enabled.any { it.id == p.id }
    PluginRow(p) {
      if (p.hasSettings)
        IconButton(onClick = { core.nav.openPluginSettings(p.id) }) {
          Icon(Icons.Outlined.Settings, contentDescription = CoreText.sectionSettingsOf(p.title)())
        }
      Switch(checked = on, onCheckedChange = { core.plugins.setEnabled(p.id, it) })
      IconButton(onClick = { removing = p }) {
        Icon(
          BerillIcons.Delete,
          contentDescription = CoreText.removeSectionOf(p.title)(),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
  Text(
    CoreText.sectionsHint(),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )

  removing?.let { p ->
    ConfirmDialog(
      title = CoreText.removeSectionTitle(p.title)(),
      text = CoreText.removeSectionText(),
      onConfirm = { core.plugins.uninstall(p.id) },
      onDismiss = { removing = null }
    )
  }
}

@Composable
internal fun AvailablePlugins(core: BerillCore) {
  val available by core.plugins.available.collectAsState()
  if (available.isEmpty()) {
    EmptyHint(CoreText.allSectionsInstalled())
    return
  }
  available.forEach { p ->
    PluginRow(p) {
      FilledTonalButton(onClick = { core.plugins.install(p.id) }) {
        Ic(BerillIcons.Add, size = 18.dp)
        Text(CoreText.install(), modifier = Modifier.padding(start = 6.dp))
      }
    }
  }
}

@Composable
private fun PluginRow(p: BerillPlugin, actions: @Composable () -> Unit) {
  Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.size(40.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Ic(p.icon, tint = MaterialTheme.colorScheme.onPrimaryContainer)
      }
    }
    Column(Modifier.weight(1f)) {
      Text(p.title, style = MaterialTheme.typography.titleSmall)
      if (p.description.isNotBlank())
        Text(
          p.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) { actions() }
  }
}

@Composable
internal fun PluginSettingsScreen(core: BerillCore, pluginId: String) {
  val plugin = core.plugins.byId(pluginId)
  val enabled by core.plugins.enabled.collectAsState()
  Column(
    Modifier.fillMaxSize().mouseScrollable(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    when {
      plugin == null -> EmptyHint(CoreText.sectionMissing())
      !plugin.hasSettings -> EmptyHint(CoreText.sectionNoSettings())
      else -> {
        if (enabled.none { it.id == plugin.id }) EmptyHint(CoreText.sectionDisabledSettingsHint())
        PluginScope(plugin) { plugin.Settings(core.plugins.context(plugin)) }
      }
    }
    Box(Modifier.width(1.dp))
  }
}
