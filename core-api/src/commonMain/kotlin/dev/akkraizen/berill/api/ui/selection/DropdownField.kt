package dev.akkraizen.berill.api.ui.selection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.*
import dev.akkraizen.berill.api.ui.display.Ic

@Composable
fun <T> DropdownField(
  label: String,
  options: List<T>,
  selected: T?,
  text: (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  icon: ((T) -> ImageVector?)? = null
) {
  var open by remember { mutableStateOf(false) }
  Box(modifier) {
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
      val selectedIcon = if (selected != null) icon?.invoke(selected) else null
      if (selectedIcon != null) {
        Ic(selectedIcon, size = 18.dp)
        Spacer(Modifier.width(8.dp))
      }
      Text(
        if (selected == null) label else "$label: ${text(selected)}",
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Ic(BerillIcons.Dropdown)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      options.forEach { option ->
        val optionIcon = icon?.invoke(option)
        DropdownMenuItem(
          text = { Text(text(option)) },
          onClick = {
            onSelect(option)
            open = false
          },
          leadingIcon = if (optionIcon != null) {
            { Ic(optionIcon) }
          } else
            null
        )
      }
    }
  }
}
