package dev.akkraizen.berill.core.connections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n

@Composable
internal fun FeatureErrorDialog(errors: FeatureErrors) {
  val problem by errors.problem.collectAsState()
  problem?.let { issue -> AlertDialog(onDismissRequest = errors::dismiss,
    icon = { Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
    title = { Text(issue.title()) }, text = { Text(issue.message()) },
    confirmButton = { TextButton(onClick = errors::dismiss) { Text(ConnectionsText.close()) } }) }
}

@Composable
internal fun FeatureErrorNotice(message: String, retry: () -> Unit) {
  Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(22.dp))
      Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
      TextButton(onClick = retry, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text(I18n.text("errors.retry")) }
    }
  }
}