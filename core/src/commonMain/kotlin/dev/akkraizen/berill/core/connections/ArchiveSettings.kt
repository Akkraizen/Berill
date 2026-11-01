package dev.akkraizen.berill.core.connections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.cards.SectionCard
import androidx.compose.runtime.*
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.core.BerillCore
import kotlinx.coroutines.*

@Composable
internal fun ArchiveSettings(core: BerillCore) {
  val scope = rememberCoroutineScope()
  val capabilities by remember(core) { core.plugins.capabilities.observe<Capability>() }
    .collectAsState(core.plugins.capabilities.all<Capability>())
  var healthBusy by remember { mutableStateOf(false) }
  var remindersBusy by remember { mutableStateOf(false) }
  SectionCard(ConnectionsText.dataTools(), Icons.Outlined.SettingsBackupRestore) {
  core.healthImport?.takeIf { capabilities.any { it is HealthDataSink } }?.let { health ->
    val state by health.status.collectAsState()
    ConnectionAction("Health Connect", Icons.Outlined.MonitorHeart, enabled = !healthBusy) { scope.launch {
      if (healthBusy) return@launch
      healthBusy = true
      try { core.connections.errors.runSuspending(FailureOperation.HEALTH) { health.connect() } }
      finally { healthBusy = false }
    } }
    if (state.isNotBlank()) Text(state)
  }
  val reminders by core.reminders.enabled.collectAsState()
  if (capabilities.any { it is ReminderSource || it is DeadlineSource }) Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
      Text(dev.akkraizen.berill.api.i18n.Tr("connections.reminders")(), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
      Switch(checked = reminders, enabled = !remindersBusy, onCheckedChange = { value -> scope.launch {
        if (remindersBusy) return@launch
        remindersBusy = true
        try { core.connections.errors.runSuspending(FailureOperation.REMINDERS) { core.reminders.setEnabled(value) } }
        finally { remindersBusy = false }
      } })
    }
  }
  Text(ConnectionsText.archiveHint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  var busy by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf<String?>(null) }
  var blobs by remember { mutableStateOf(emptyMap<String, ByteArray>()) }
  var pending by remember { mutableStateOf<List<dev.akkraizen.berill.protocol.SyncRecord>?>(null) }
  ConnectionAction(ConnectionsText.export(), Icons.Outlined.FileDownload, enabled = !busy, onClick = { scope.launch {
    if (busy) return@launch
    busy = true; status = null
    core.notifications.operation(FailureOperation.EXPORT, FeatureProgress.RUNNING)
    try {
      val records = core.store.records()
      val hashes = Regex("blob:([0-9a-f]{64})").findAll(records.joinToString { it.fields.toString() }).map { it.groupValues[1] }.toSet()
      val attachments = hashes.associateWith { hash -> (core.attachments.get(hash) ?: throw FeatureException(FailureReason.MISSING_ATTACHMENT)) }
      val bytes = withContext(Dispatchers.Default) { encodeLocalArchive(records, attachments) }
      val saved = core.files.saveFile("berill-${today()}.zip", bytes, "application/zip")
      if (saved) status = ConnectionsText.saved()
      core.notifications.operation(FailureOperation.EXPORT, if (saved) FeatureProgress.SUCCESS else FeatureProgress.CANCELLED)
    } catch (e: CancellationException) { core.notifications.operation(FailureOperation.EXPORT, FeatureProgress.CANCELLED); throw e } catch (e: Exception) { core.connections.errors.report(FailureOperation.EXPORT, e) } finally { busy = false }
  } })
  ConnectionAction(ConnectionsText.importArchive(), Icons.Outlined.FileUpload, enabled = !busy, onClick = { scope.launch {
    if (busy) return@launch
    busy = true; status = null
    try {
      val file = core.files.pickFile(listOf("zip"), 256 * 1024 * 1024)
      if (file != null) {
        val decoded = withContext(Dispatchers.Default) {
          try { decodeLocalArchive(file.bytes) to decodeArchiveAttachments(file.bytes) }
          catch (e: CancellationException) { throw e } catch (e: Exception) { throw FeatureException(FailureReason.INVALID_DATA, e) }
        }
        val hashes = Regex("blob:([0-9a-f]{64})").findAll(decoded.first.joinToString { it.fields.toString() }).map { it.groupValues[1] }.toSet()
        if (!hashes.all { it in decoded.second || core.attachments.cache.get(it) != null }) throw FeatureException(FailureReason.MISSING_ATTACHMENT)
        blobs = decoded.second; pending = decoded.first
      }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { core.connections.errors.report(FailureOperation.IMPORT, e) } finally { busy = false }
  } })
  if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
  status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
  pending?.let { records -> AlertDialog(onDismissRequest = { if (!busy) pending = null },
    icon = { Icon(Icons.Outlined.SettingsBackupRestore, null) },
    title = { Text(ConnectionsText.importArchive()) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(ConnectionsText.importConfirm()); Text(records.size.toString(), style = MaterialTheme.typography.titleLarge) } },
    confirmButton = { FilledTonalButton(enabled = !busy, onClick = {
      scope.launch {
        if (busy) return@launch
        busy = true; status = null
        core.notifications.operation(FailureOperation.IMPORT, FeatureProgress.RUNNING)
        try {
          for (bytes in blobs.values) core.attachments.put(bytes)
          core.store.applyRemote(records); core.store.enqueueAllForSync(); pending = null; status = ConnectionsText.saved()
          core.notifications.operation(FailureOperation.IMPORT, FeatureProgress.SUCCESS)
        } catch (e: CancellationException) { core.notifications.operation(FailureOperation.IMPORT, FeatureProgress.CANCELLED); throw e } catch (e: Exception) { core.connections.errors.report(FailureOperation.IMPORT, e) } finally { busy = false }
      }
    }) { Text(ConnectionsText.restore()) } },
    dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text(ConnectionsText.close()) } }) }
  }
}
