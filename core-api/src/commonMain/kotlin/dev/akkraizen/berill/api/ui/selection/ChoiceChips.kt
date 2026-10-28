package dev.akkraizen.berill.api.ui.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.BerillChip

@Composable
fun <T> ChoiceChips(
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  icon: ((T) -> ImageVector?)? = null
) {
  FlowRow(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    options.forEach { option ->
      val vector = icon?.invoke(option)
      BerillChip(
        selected = option == selected,
        onClick = { onSelect(option) },
        label = { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (vector != null) {
          { Ic(vector, size = 18.dp) }
        } else
          null
      )
    }
  }
}
