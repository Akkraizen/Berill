package dev.akkraizen.berill.core.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.ddMMyyyy
import dev.akkraizen.berill.api.ui.hhmmss
import dev.akkraizen.berill.api.ui.localDateTime
import dev.akkraizen.berill.api.ui.navigation.rememberOpenLink
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.BuildInfo
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.sync.E2eState
import dev.akkraizen.berill.core.sync.SyncDiagnostics
import dev.akkraizen.berill.core.ui.CoreText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object DiagText {
  val about = Tr("core.about")
  fun version(version: String, code: Int) = Tr("core.diag.version", "version" to version, "code" to code)
  fun commit(commit: String, date: String) = Tr("core.diag.commit", "commit" to commit, "date" to date)
  val dirty = Tr("core.diag.dirty")
  val details = Tr("core.diag.details")
  val hideDetails = Tr("core.diag.hideDetails")
  val server = Tr("core.diag.server")
  val account = Tr("core.diag.account")
  val dataset = Tr("core.diag.dataset")
  val cursor = Tr("core.diag.cursor")
  val pending = Tr("core.diag.pending")
  fun pendingExcluded(count: Int) = Tr("core.diag.pendingExcluded", "count" to count)
  val total = Tr("core.diag.total")
  val lastOk = Tr("core.diag.lastOk")
  val lastError = Tr("core.diag.lastError")
  val encryption = Tr("core.diag.encryption")
  val encOff = Tr("core.diag.encOff")
  val encOn = Tr("core.diag.encOn")
  val encLocked = Tr("core.diag.encLocked")
  val none = Tr("core.diag.none")
  val never = Tr("core.diag.never")
  val noToken = Tr("core.diag.noToken")
  val unknownUser = Tr("core.diag.unknownUser")
  val fullResendPending = Tr("core.diag.fullResendPending")
  val resendAll = Tr("core.diag.resendAll")
  val resendAllHint = Tr("core.diag.resendAllHint")
  val copyReport = Tr("core.diag.copyReport")
  val copied = Tr("core.diag.copied")
  val log = Tr("core.diag.log")
  val logTitle = Tr("core.diag.logTitle")
  val logEmpty = Tr("core.diag.logEmpty")
  val openFolder = Tr("core.diag.openFolder")
  val copy = Tr("core.diag.copy")
  val close = Tr("core.diag.close")
  val serverPage = Tr("core.diag.serverPage")
}

private fun formatTime(millis: Long?): String {
  if (millis == null) return DiagText.never()
  val t = localDateTime(millis)
  return "${t.date.ddMMyyyy()} ${t.hhmmss()}"
}

internal fun versionLine(): String = DiagText.version(BuildInfo.VERSION_NAME, BuildInfo.VERSION_CODE)()

internal fun commitLine(): String =
  DiagText.commit(BuildInfo.COMMIT, BuildInfo.COMMIT_TIME.take(10))() +
    if (BuildInfo.DIRTY) " · ${DiagText.dirty()}" else ""

private fun encryptionLabel(state: E2eState) = when (state) {
  E2eState.Off -> DiagText.encOff()
  E2eState.On -> DiagText.encOn()
  E2eState.Locked -> DiagText.encLocked()
}

private fun rows(d: SyncDiagnostics): List<Pair<String, String>> = listOf(
  DiagText.server() to ((d.serverUrl ?: DiagText.none()) + if (d.hasToken) "" else " · ${DiagText.noToken()}"),
  DiagText.account() to (d.user ?: DiagText.unknownUser()),
  DiagText.dataset() to (d.dataset ?: DiagText.none()),
  DiagText.cursor() to d.cursor.toString(),
  DiagText.pending() to (d.pending.toString() +
    if (d.pendingExcluded > 0) " · ${DiagText.pendingExcluded(d.pendingExcluded)()}" else ""),
  DiagText.total() to d.totalRecords.toString(),
  DiagText.lastOk() to formatTime(d.lastOkMillis),
  DiagText.lastError() to (d.lastError?.let { "${formatTime(d.lastErrorMillis)} — $it" } ?: DiagText.none()),
  DiagText.encryption() to encryptionLabel(d.encryption),
)

private fun report(d: SyncDiagnostics): String = buildString {
  appendLine(versionLine())
  appendLine(commitLine())
  appendLine(CoreText.device(d.deviceId)())
  rows(d).forEach { (k, v) -> appendLine("$k: $v") }
  if (d.fullResendPending) appendLine(DiagText.fullResendPending())
  appendLine()
  appendLine("--- ${DiagText.logTitle()} ---")
  append(AppLog.tail(8_000))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SyncDetails(core: BerillCore) {
  val sync = core.sync
  val state by sync.state.collectAsState()
  var expanded by remember { mutableStateOf(false) }
  var showLog by remember { mutableStateOf(false) }
  var copied by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  @Suppress("DEPRECATION")
  val clipboard = LocalClipboardManager.current
  val openLink = rememberOpenLink()

  TextButton(onClick = { expanded = !expanded }) {
    Text(if (expanded) DiagText.hideDetails() else DiagText.details())
  }
  AnimatedVisibility(expanded) {
    val diagnostics by produceState<SyncDiagnostics?>(null, state) {
      value = withContext(Dispatchers.Default) { runCatching { sync.diagnostics() }.getOrNull() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      val d = diagnostics
      if (d != null) {
        rows(d).forEach { (label, value) -> DetailRow(label, value) }
        if (d.fullResendPending) {
          Text(
            DiagText.fullResendPending(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
          )
        }
      }
      Text(
        DiagText.resendAllHint(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { scope.launch { sync.resendAll() } }) { Text(DiagText.resendAll()) }
        OutlinedButton(onClick = {
          scope.launch {
            val text = withContext(Dispatchers.Default) { report(sync.diagnostics()) }
            clipboard.setText(AnnotatedString(text))
            copied = true
            delay(2_000)
            copied = false
          }
        }) { Text(if (copied) DiagText.copied() else DiagText.copyReport()) }
        OutlinedButton(onClick = { showLog = true }) { Text(DiagText.log()) }
        diagnostics?.serverUrl?.let { url ->
          OutlinedButton(onClick = { openLink("$url/status") }) { Text(DiagText.serverPage()) }
        }
      }
    }
  }
  if (showLog) LogDialog(onDismiss = { showLog = false })
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(0.45f)
    )
    SelectionContainer(Modifier.weight(0.55f)) {
      Text(value, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis, maxLines = 4)
    }
  }
}

@Composable
internal fun LogDialog(onDismiss: () -> Unit) {
  @Suppress("DEPRECATION")
  val clipboard = LocalClipboardManager.current
  val text by produceState("") { value = withContext(Dispatchers.Default) { AppLog.tail() } }
  val open = AppLog.openLocation
  val vertical = rememberScrollState()
  LaunchedEffect(text) { vertical.scrollTo(vertical.maxValue) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(DiagText.logTitle()) },
    text = {
      SelectionContainer {
        Text(
          text.ifEmpty { DiagText.logEmpty() },
          fontFamily = FontFamily.Monospace,
          style = MaterialTheme.typography.bodySmall,
          softWrap = false,
          modifier = Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(vertical)
            .horizontalScroll(rememberScrollState())
            .padding(end = 8.dp)
        )
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(DiagText.close()) } },
    dismissButton = {
      Row {
        if (open != null) TextButton(onClick = { runCatching { open() } }) { Text(DiagText.openFolder()) }
        TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text(DiagText.copy()) }
      }
    }
  )
}

@Composable
internal fun AboutApp(core: BerillCore) {
  var showLog by remember { mutableStateOf(false) }
  Text(versionLine(), style = MaterialTheme.typography.bodyMedium)
  Text(commitLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  SelectionContainer {
    Text(
      CoreText.device(core.deviceId)(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
  UpdateSection(core)
  OutlinedButton(onClick = { showLog = true }) { Text(DiagText.log()) }
  if (showLog) LogDialog(onDismiss = { showLog = false })
}
