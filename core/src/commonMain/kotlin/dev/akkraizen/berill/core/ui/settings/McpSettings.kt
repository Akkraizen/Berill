package dev.akkraizen.berill.core.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.core.platform.McpAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object McpText {
  val title = Tr("core.mcp.title")
  val hint = Tr("core.mcp.hint")
  val enable = Tr("core.mcp.enable")
  val enableHint = Tr("core.mcp.enableHint")
  val writable = Tr("core.mcp.writable")
  val writableHint = Tr("core.mcp.writableHint")
  val starting = Tr("core.mcp.starting")
  val desktopTitle = Tr("core.mcp.desktopTitle")
  val desktopHint = Tr("core.mcp.desktopHint")
  val desktopHttpHint = Tr("core.mcp.desktopHttpHint")
  val codeTitle = Tr("core.mcp.codeTitle")
  val codeHint = Tr("core.mcp.codeHint")
  val token = Tr("core.mcp.token")
  val tokenHint = Tr("core.mcp.tokenHint")
  val showToken = Tr("core.mcp.showToken")
  val hideToken = Tr("core.mcp.hideToken")
  val regenerate = Tr("core.mcp.regenerate")
  val regenerateQuestion = Tr("core.mcp.regenerateQuestion")
  val regenerateText = Tr("core.mcp.regenerateText")
  val copy = Tr("core.mcp.copy")
  val copied = Tr("core.mcp.copied")
  val cancel = Tr("core.cancel")

  fun running(url: String) = I18n.text("core.mcp.running", "url" to url)

  fun failed(reason: String) = I18n.text("core.mcp.failed", "reason" to reason)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun McpSettings(mcp: McpAccess) {
  val status by mcp.status.collectAsState()
  val scope = rememberCoroutineScope()
  var showToken by remember { mutableStateOf(false) }
  var askRegenerate by remember { mutableStateOf(false) }
  var version by remember { mutableStateOf(0) }

  fun background(action: () -> Unit) {
    scope.launch {
      withContext(Dispatchers.Default) { action() }
      version++
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(McpText.hint(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SettingSwitch(
      McpText.enable(),
      when {
        !status.enabled -> McpText.enableHint()
        status.error != null -> McpText.failed(status.error.orEmpty())
        status.url != null -> McpText.running(status.url.orEmpty())
        else -> McpText.starting()
      },
      status.enabled
    ) { enable -> background { mcp.setEnabled(enable) } }
    AnimatedVisibility(status.enabled) {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingSwitch(McpText.writable(), McpText.writableHint(), status.writable) { on -> background { mcp.setWritable(on) } }
        val desktopConfig = remember(status, version) { mcp.desktopClientConfig() }
        CodeBlock(
          McpText.desktopTitle(),
          if (mcp.bridgeCommand != null) McpText.desktopHint() else McpText.desktopHttpHint(),
          desktopConfig
        )
        val codeCommand = remember(status, version) { mcp.claudeCodeCommand() }
        CodeBlock(McpText.codeTitle(), McpText.codeHint(), codeCommand)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(McpText.token(), style = MaterialTheme.typography.titleSmall)
          Text(McpText.tokenHint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (showToken) {
            val token = remember(version) { mcp.token() }
            Monospace(token)
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showToken = !showToken }) { Text(if (showToken) McpText.hideToken() else McpText.showToken()) }
            CopyButton { mcp.token() }
            OutlinedButton(onClick = { askRegenerate = true }) { Text(McpText.regenerate()) }
          }
        }
      }
    }
  }

  if (askRegenerate)
    AlertDialog(
      onDismissRequest = { askRegenerate = false },
      title = { Text(McpText.regenerateQuestion()) },
      text = { Text(McpText.regenerateText()) },
      confirmButton = {
        TextButton(onClick = {
          askRegenerate = false
          background { mcp.regenerateToken() }
        }) { Text(McpText.regenerate()) }
      },
      dismissButton = { TextButton(onClick = { askRegenerate = false }) { Text(McpText.cancel()) } }
    )
}

@Composable
private fun CodeBlock(title: String, hint: String, code: String) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Monospace(code)
    CopyButton { code }
  }
}

@Composable
private fun Monospace(text: String) {
  Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
    SelectionContainer {
      Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
  }
}

@Composable
private fun CopyButton(text: () -> String) {
  @Suppress("DEPRECATION")
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  var copied by remember { mutableStateOf(false) }
  OutlinedButton(onClick = {
    clipboard.setText(AnnotatedString(text()))
    scope.launch {
      copied = true
      delay(2_000)
      copied = false
    }
  }) { Text(if (copied) McpText.copied() else McpText.copy()) }
}
