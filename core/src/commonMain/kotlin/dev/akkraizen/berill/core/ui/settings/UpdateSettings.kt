package dev.akkraizen.berill.core.ui.settings

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
import dev.akkraizen.berill.api.ui.navigation.rememberOpenLink
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
internal fun UpdateBanner(update: AppUpdate, modifier: Modifier = Modifier) {
  val open = rememberOpenLink()
  Surface(
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    shape = RoundedCornerShape(16.dp),
    modifier = modifier.fillMaxWidth()
  ) {
    FlowRow(
      Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Column(Modifier.padding(top = 4.dp)) {
        Text(UpdateText.available(update.version)(), style = MaterialTheme.typography.titleSmall)
        Text(
          if (update.url != null) UpdateText.current(BuildInfo.VERSION_NAME)() else UpdateText.noFile(),
          style = MaterialTheme.typography.bodySmall
        )
      }
      val url = update.url
      if (url != null) FilledTonalButton(onClick = { open(url) }) { Text(UpdateText.download()) }
    }
  }
}

@Composable
internal fun UpdateSection(core: BerillCore) {
  val updates = core.updates
  val check by updates.state.collectAsState()
  val scope = rememberCoroutineScope()
  var url by remember { mutableStateOf(updates.serverUrl()) }

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
  when (val c = check) {
    is UpdateCheck.Available -> UpdateBanner(c.update)
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
      enabled = check !is UpdateCheck.Checking
    ) { Text(UpdateText.check()) }
}

@Composable
internal fun HomeUpdateBanner(core: BerillCore, modifier: Modifier = Modifier) {
  val check by core.updates.state.collectAsState()
  (check as? UpdateCheck.Available)?.let { UpdateBanner(it.update, modifier) }
}
