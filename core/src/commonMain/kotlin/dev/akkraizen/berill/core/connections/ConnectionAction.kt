package dev.akkraizen.berill.core.connections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun ConnectionAction(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(18.dp), color = colors.surfaceContainerHigh) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(icon, null, Modifier.size(24.dp), tint = if (enabled) colors.primary else colors.onSurface.copy(alpha = .38f))
      Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
      Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
    }
  }
}
