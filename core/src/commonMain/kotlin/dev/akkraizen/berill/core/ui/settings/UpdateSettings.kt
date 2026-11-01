package dev.akkraizen.berill.core.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.BuildInfo
import dev.akkraizen.berill.core.update.AppUpdate
import dev.akkraizen.berill.core.update.UpdateCheck
import kotlinx.coroutines.launch

internal object UpdateText {
  fun available(version: String) = Tr("core.update.available", "version" to version)
  fun current(version: String) = Tr("core.update.current", "version" to version)
  val download = Tr("core.update.download")
  val check = Tr("core.update.check")
  val checking = Tr("core.update.checking")
  val upToDate = Tr("core.update.upToDate")
  fun failed(message: String) = Tr("core.update.failed", "message" to message)
  val noFile = Tr("core.update.noFile")
  val needServer = Tr("core.update.needServer")
  val server = Tr("core.update.server")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpdateBanner(core: BerillCore, update: AppUpdate, modifier: Modifier = Modifier) {
  val updates = core.updates
  val transfer by updates.transfer.collectAsState()
  val ready = transfer as? dev.akkraizen.berill.core.update.UpdateTransfer.Ready
    ?: (transfer as? dev.akkraizen.berill.core.update.UpdateTransfer.Failed)?.ready
  val shown = ready?.update ?: update
  Surface(color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(UpdateText.available(shown.version)(), style = MaterialTheme.typography.titleSmall)
      Text(UpdateText.current(BuildInfo.VERSION_NAME)(), style = MaterialTheme.typography.bodySmall)
      shown.notes?.takeIf { it.isNotBlank() }?.let { notes ->
        Text(notes, modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
      }
      when (val stage = transfer) {
        is dev.akkraizen.berill.core.update.UpdateTransfer.Downloading -> {
          val total = stage.total ?: 0
          dev.akkraizen.berill.api.ui.feedback.ProgressLine(if (total > 0) stage.received.toFloat() / total else 0f)
          Text(Tr("core.update.progress", "received" to (stage.received / 1024 / 1024), "total" to (total / 1024 / 1024))(), style = MaterialTheme.typography.bodySmall)
        }
        is dev.akkraizen.berill.core.update.UpdateTransfer.Failed -> Text(Tr("core.update.error.${stage.reason.name}")(), color = MaterialTheme.colorScheme.error)
        is dev.akkraizen.berill.core.update.UpdateTransfer.Installing -> Text(Tr("core.update.installing")())
        else -> if (ready != null) Text(Tr(when (ready.hint) {
          dev.akkraizen.berill.core.update.InstallResult.PERMISSION_REQUIRED -> "core.update.permission"
          dev.akkraizen.berill.core.update.InstallResult.OPENED -> "core.update.opened"
          else -> "core.update.ready"
        })(), style = MaterialTheme.typography.bodySmall)
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
          transfer is dev.akkraizen.berill.core.update.UpdateTransfer.Downloading -> TextButton(onClick = updates::cancel) { Text(Tr("core.update.cancel")()) }
          transfer is dev.akkraizen.berill.core.update.UpdateTransfer.Installing -> Unit
          ready != null -> {
            FilledTonalButton(onClick = updates::install) { Text(Tr(if (dev.akkraizen.berill.core.sync.clientPlatform == "windows") "core.update.restartInstall" else "core.update.install")()) }
            TextButton(onClick = updates::cancel) { Text(Tr("core.update.discard")()) }
          }
          shown.url == null -> Text(UpdateText.noFile())
          !updates.installationAvailable -> Text(Tr("core.update.error.UNSUPPORTED")())
          shown.sha256 == null -> Text(Tr("core.update.error.SERVER_TOO_OLD")())
          else -> FilledTonalButton(onClick = { updates.download(shown) }) { Text(Tr("core.update.downloadInApp")()) }
        }
      }
    }
  }
}

@Composable
internal fun UpdateSection(core: BerillCore) {
  val updates = core.updates
  val check by updates.state.collectAsState()
  val scope = rememberCoroutineScope()
  var url by remember { mutableStateOf(updates.serverUrl()) }
  androidx.compose.runtime.LaunchedEffect(updates) { if (updates.state.value == UpdateCheck.Unknown) updates.check() }
  if (check !is UpdateCheck.Available) Text(UpdateText.current(BuildInfo.VERSION_NAME)(), style = MaterialTheme.typography.titleSmall)

  CommitTextField(
    value = url,
    onCommit = { value ->
      updates.setServerUrl(value)
      url = updates.serverUrl()
    },
    label = UpdateText.server(),
    modifier = Modifier.fillMaxWidth(),
    keyboardType = KeyboardType.Uri,
    placeholder = "https://updates.berill.space"
  )
  val transfer by updates.transfer.collectAsState()
  if (check !is UpdateCheck.Available && transfer is dev.akkraizen.berill.core.update.UpdateTransfer.Failed) {
    Text(Tr("core.update.error.${(transfer as dev.akkraizen.berill.core.update.UpdateTransfer.Failed).reason.name}")(), color = MaterialTheme.colorScheme.error)
  }
  when (val c = check) {
    is UpdateCheck.Available -> UpdateBanner(core, c.update)
    else -> {
      val text = when {
        url.isEmpty() -> UpdateText.needServer()
        c is UpdateCheck.Checking -> UpdateText.checking()
        c is UpdateCheck.UpToDate -> UpdateText.upToDate()
        c is UpdateCheck.Failed -> UpdateText.failed(c.message)()
        else -> null
      }
      if (text != null)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
  if (url.isNotEmpty())
    TextButton(
      onClick = { scope.launch { updates.check() } },
      enabled = check !is UpdateCheck.Checking && transfer !is dev.akkraizen.berill.core.update.UpdateTransfer.Downloading && transfer !is dev.akkraizen.berill.core.update.UpdateTransfer.Installing && transfer !is dev.akkraizen.berill.core.update.UpdateTransfer.Ready
    ) { Text(UpdateText.check()) }
}

@Composable
internal fun HomeUpdateBanner(core: BerillCore, modifier: Modifier = Modifier) {
  val check by core.updates.state.collectAsState()
  (check as? UpdateCheck.Available)?.let { UpdateBanner(core, it.update, modifier) }
}
