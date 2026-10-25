package dev.akkraizen.berill.core.external

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.sdk.protocol.PACKAGE_EXTENSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ExternalPluginsSettings(core: BerillCore) {
  val platform = core.external.platform
  if (platform == null) {
    EmptyHint(ExternalText.notSupported())
    return
  }
  val scope = rememberCoroutineScope()
  var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
  var inspection by remember { mutableStateOf<PackageInspection?>(null) }
  var message by remember { mutableStateOf<String?>(null) }
  var removing by remember { mutableStateOf<ExternalPluginProxy?>(null) }
  var pendingChanges by remember {
    mutableStateOf(runCatching { platform.pendingChanges() }.getOrDefault(emptySet()))
  }

  Text(
    ExternalText.sectionHint(),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )

  if (core.external.loaded.isEmpty() && core.external.rejected.isEmpty())
    EmptyHint(ExternalText.none())
  core.external.loaded.forEach { p ->
    val status by
      remember(p) {
        p.activeClient?.status ?: MutableStateFlow<String?>(null)
      }
        .collectAsState()
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.size(40.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Ic(p.icon, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
      }
      Column(Modifier.weight(1f)) {
        Text(p.title, style = MaterialTheme.typography.titleSmall)
        Text(
          listOfNotNull(
            ExternalText.version(p.manifest.version, p.manifest.author)(),
            if (p.id in pendingChanges)
              ExternalText.restartNeeded()
            else
              ExternalText.running()
          )
            .joinToString(" · "),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          permissionsLine(p.manifest.permissions.network),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        status?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
      IconButton(onClick = { removing = p }) {
        Icon(
          BerillIcons.Delete,
          contentDescription = ExternalText.remove(),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
  core.external.rejected.forEach { (id, problems) ->
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Ic(Icons.Outlined.ErrorOutline, tint = MaterialTheme.colorScheme.error)
      Column {
        Text(ExternalText.rejected(id)(), style = MaterialTheme.typography.titleSmall)
        problems.forEach {
          Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }

  FilledTonalButton(
    onClick = {
      scope.launch {
        val file = core.files.pickFile(listOf(PACKAGE_EXTENSION)) ?: return@launch
        val result = withContext(Dispatchers.Default) { platform.inspect(file.bytes) }
        pendingBytes = file.bytes
        inspection = result
      }
    }
  ) {
    Ic(Icons.Outlined.FileOpen, size = 18.dp)
    Text(ExternalText.installFromFile(), modifier = Modifier.padding(start = 6.dp))
  }
  message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

  inspection?.let { result ->
    InstallDialog(
      result = result,
      onDismiss = {
        inspection = null
        pendingBytes = null
      },
      onInstall = {
        val bytes = pendingBytes ?: return@InstallDialog
        inspection = null
        pendingBytes = null
        scope.launch {
          val installed = withContext(Dispatchers.Default) { platform.install(bytes) }
          message = if (installed.ok) {
            ExternalText.installed(installed.manifest!!.name.get(I18n.language.code))()
          } else {
            ExternalText.badPackage() + ": " + installed.problems.joinToString("; ")
          }
          pendingChanges = runCatching { platform.pendingChanges() }.getOrDefault(emptySet())
        }
      }
    )
  }
  removing?.let { p ->
    ConfirmDialog(
      title = ExternalText.removeTitle(p.title)(),
      text = ExternalText.removeText(),
      onConfirm = {
        platform.uninstall(p.id)
        pendingChanges = runCatching { platform.pendingChanges() }.getOrDefault(emptySet())
      },
      onDismiss = { removing = null }
    )
  }
}

@Composable
private fun permissionsLine(network: List<String>): String =
  if (network.isEmpty())
    ExternalText.noNetwork()
  else
    ExternalText.network(network.joinToString(", "))()

@Composable
private fun InstallDialog(result: PackageInspection, onDismiss: () -> Unit, onInstall: () -> Unit) {
  val m = result.manifest
  val ok = result.ok
  DialogKeys(onConfirm = if (ok) onInstall else null, confirmEnabled = ok, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(if (ok) Icons.Outlined.Shield else Icons.Outlined.ErrorOutline, contentDescription = null)
    },
    title = { Text(if (ok) ExternalText.installTitle() else ExternalText.badPackage()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (m != null) {
          Text(m.name.get(I18n.language.code), style = MaterialTheme.typography.titleMedium)
          Text(
            ExternalText.version(m.version, m.author)(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (m.description.get(I18n.language.code).isNotBlank()) Text(m.description.get(I18n.language.code))
        }
        if (ok && m != null) {
          Text(ExternalText.permissions(), style = MaterialTheme.typography.labelLarge)
          Text("• " + permissionsLine(m.permissions.network))
          Text("• " + ExternalText.collections(m.collections.size)())
          Text("• " + ExternalText.pages(m.pages.size)())
          Text(
            ExternalText.sectionHint(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        result.problems.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
      }
    },
    confirmButton = { if (ok) TextButton(onClick = onInstall) { Text(ExternalText.install()) } },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(if (ok) ExternalText.cancel() else ExternalText.close())
      }
    }
  )
}
