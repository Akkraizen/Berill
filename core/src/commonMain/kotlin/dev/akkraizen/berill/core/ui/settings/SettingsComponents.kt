package dev.akkraizen.berill.core.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.interactive

internal data class DataSection(
  val title: String,
  val icon: ImageVector,
  val collections: List<String>,
  val enabled: Boolean
)

@Composable
internal fun SettingsSubtitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 4.dp)
  )
}

@Composable
internal fun SettingSwitch(
  title: String,
  subtitle: String?,
  checked: Boolean,
  icon: ImageVector? = null,
  onChange: (Boolean) -> Unit
) {
  Row(
    Modifier.fillMaxWidth().interactive(bleed = 6.dp) { onChange(!checked) },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    if (icon != null) Ic(icon, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.weight(1f)) {
      Text(title)
      if (subtitle != null)
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
