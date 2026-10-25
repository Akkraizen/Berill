package dev.akkraizen.berill.api.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.*
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.interaction.DialogKeys

@Composable
fun ConfirmDialog(
  title: String,
  text: String,
  confirm: String = UiText.delete(),
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  DialogKeys(
    onConfirm = {
      onConfirm()
      onDismiss()
    },
    onDismiss = onDismiss
  )
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = {
      TextButton(
        onClick = {
          onConfirm()
          onDismiss()
        }
      ) {
        Text(confirm)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.cancel()) } }
  )
}

@Composable
fun DeleteButton(what: String, onDelete: () -> Unit) {
  var ask by remember { mutableStateOf(false) }
  TextButton(onClick = { ask = true }) {
    Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.error)
    Spacer(Modifier.width(6.dp))
    Text(UiText.delete(), color = MaterialTheme.colorScheme.error)
  }
  if (ask)
    ConfirmDialog(
      UiText.deleteWhat(what)(),
      UiText.deleteEverywhere(),
      onConfirm = onDelete,
      onDismiss = { ask = false }
    )
}
