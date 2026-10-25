package dev.akkraizen.berill.core.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.sync.E2eState
import dev.akkraizen.berill.core.sync.SyncEncryption
import dev.akkraizen.berill.core.ui.CoreText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EncryptionSettings(core: BerillCore) {
  val encryption = core.encryption
  val state by encryption.state.collectAsState()
  val scope = rememberCoroutineScope()
  var password by remember { mutableStateOf("") }
  var repeat by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var confirmEnable by remember { mutableStateOf(false) }
  var confirmForget by remember { mutableStateOf(false) }

  SettingsSubtitle(CoreText.e2eTitle())
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Ic(
      if (state == E2eState.On) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
      tint = if (state == E2eState.On)
        MaterialTheme.colorScheme.primary
      else
        MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      when (state) {
        E2eState.Off -> CoreText.e2eOffHint()
        E2eState.Locked -> CoreText.e2eLockedHint()
        E2eState.On -> CoreText.e2eOnHint()
      },
      style = MaterialTheme.typography.bodySmall,
      color = if (state == E2eState.Locked)
        MaterialTheme.colorScheme.error
      else
        MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f)
    )
  }

  when (state) {
    E2eState.Off -> {
      PasswordField(password, CoreText.e2ePassword()) {
        password = it
        error = null
      }
      PasswordField(repeat, CoreText.e2eRepeat()) {
        repeat = it
        error = null
      }
      val valid = password.length >= SyncEncryption.MIN_PASSWORD && password == repeat
      if (!valid && (password.isNotEmpty() || repeat.isNotEmpty())) {
        Text(
          CoreText.e2eShort(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      FilledTonalButton(onClick = { confirmEnable = true }, enabled = valid && !busy) {
        Text(CoreText.e2eEnable())
      }
    }

    E2eState.Locked -> {
      PasswordField(password, CoreText.e2ePassword()) {
        password = it
        error = null
      }
      FilledTonalButton(
        enabled = password.isNotEmpty() && !busy,
        onClick = {
          busy = true
          scope.launch {
            val ok = withContext(Dispatchers.Default) { encryption.unlock(password) }
            busy = false
            if (ok) {
              password = ""
              core.sync.resetCursor()
            } else {
              error = CoreText.e2eWrongPassword()
            }
          }
        }
      ) {
        Text(CoreText.e2eUnlock())
      }
    }

    E2eState.On -> TextButton(onClick = { confirmForget = true }) { Text(CoreText.e2eForget()) }
  }
  if (busy)
    Text(
      CoreText.e2eWorking(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

  if (confirmEnable) {
    ConfirmDialog(
      title = CoreText.e2eConfirmTitle(),
      text = CoreText.e2eConfirmText(),
      confirm = CoreText.e2eEnable(),
      onConfirm = {
        busy = true
        val chosen = password
        scope.launch {
          withContext(Dispatchers.Default) { encryption.enable(chosen) }
          busy = false
          password = ""
          repeat = ""
          core.sync.syncNow()
        }
      },
      onDismiss = { confirmEnable = false }
    )
  }
  if (confirmForget) {
    ConfirmDialog(
      title = CoreText.e2eForget(),
      text = CoreText.e2eForgetText(),
      confirm = CoreText.e2eForget(),
      onConfirm = { encryption.forgetKey() },
      onDismiss = { confirmForget = false }
    )
  }
}

@Composable
private fun PasswordField(value: String, label: String, onChange: (String) -> Unit) {
  BerillTextField(
    value,
    onChange,
    label = { Text(label) },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    modifier = Modifier.fillMaxWidth()
  )
}
