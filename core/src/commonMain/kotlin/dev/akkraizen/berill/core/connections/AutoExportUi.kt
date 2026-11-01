package dev.akkraizen.berill.core.connections

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.ddMMyyyy
import dev.akkraizen.berill.api.ui.hhmm
import dev.akkraizen.berill.api.ui.localDateTime
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.attachments.Attachments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → Data: scheduled export into a folder. */
@Composable
internal fun AutoExportSection(core: BerillCore) {
  val scope = rememberCoroutineScope()
  val settings by core.autoExport.preferences.state.collectAsState()
  val status by core.autoExport.status.collectAsState()
  val busy by core.autoExport.busy.collectAsState()
  val colors = MaterialTheme.colorScheme
  Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerHigh) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Schedule, null, tint = colors.primary)
        Text(ConnectionsText.autoExport(), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Switch(checked = settings.enabled, onCheckedChange = { enabled ->
          scope.launch {
            val folder = settings.folder ?: if (enabled) core.files.pickFolder() else null
            core.autoExport.preferences.set(settings.copy(enabled = enabled && folder != null, folder = folder))
          }
        })
      }
      Text(ConnectionsText.autoExportHint(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
      if (settings.enabled) {
        settings.folder?.let { Text(ConnectionsText.autoExportFolder(core.files.folderLabel(it)), style = MaterialTheme.typography.bodyMedium) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(settings.intervalDays == 1, { core.autoExport.preferences.set(settings.copy(intervalDays = 1)) }, { Text(ConnectionsText.autoExportDaily()) })
          FilterChip(settings.intervalDays == 7, { core.autoExport.preferences.set(settings.copy(intervalDays = 7)) }, { Text(ConnectionsText.autoExportWeekly()) })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { scope.launch { core.files.pickFolder()?.let { core.autoExport.preferences.set(settings.copy(folder = it)) } } }) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(ConnectionsText.autoExportChoose())
          }
          FilledTonalButton(enabled = !busy, onClick = { scope.launch { core.autoExport.runNow() } }) { Text(ConnectionsText.autoExportNow()) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { last ->
          val time = localDateTime(last.at).let { "${it.date.ddMMyyyy()} ${it.hhmm()}" }
          val failure = last.failure
          if (failure == null) Text(ConnectionsText.autoExportLast(time), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
          else Text(ConnectionsText.autoExportFailed(FeatureProblem(FailureOperation.EXPORT, failure).message()), style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
      }
    }
  }
}

/** Settings → Data: how much disk the attachment cache may use. */
@Composable
internal fun AttachmentCacheSection(core: BerillCore) {
  var limit by remember { mutableStateOf(core.attachments.cacheLimitBytes) }
  var used by remember { mutableStateOf<Long?>(null) }
  LaunchedEffect(limit) { used = withContext(Dispatchers.Default) { core.attachments.cacheSize() } }
  val colors = MaterialTheme.colorScheme
  Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerHigh) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Storage, null, tint = colors.primary)
        Text(ConnectionsText.cacheUsage(ConnectionsText.megabytes((used ?: 0L) / MB), ConnectionsText.megabytes(limit / MB)),
          Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
      }
      Text(ConnectionsText.cacheHint(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in LIMITS) FilterChip(limit == option, {
          core.attachments.cacheLimitBytes = option
          limit = option
        }, { Text(ConnectionsText.megabytes(option / MB)) })
      }
    }
  }
}

private const val MB = 1024L * 1024
private val LIMITS = listOf(256 * MB, Attachments.DEFAULT_CACHE_LIMIT, 1024 * MB, 2048 * MB)
