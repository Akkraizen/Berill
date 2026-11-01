package dev.akkraizen.berill.api.ui.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Switches between views of one section page (for example, entries and statistics) as a Material 3
 * Expressive connected button group: equal-width toggle buttons whose shape morphs when selected and
 * pressed. Keeps the page's side margins and drops keyboard focus after a click, so no highlight
 * stays behind on desktop.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BerillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  val focus = LocalFocusManager.current
  val colors = MaterialTheme.colorScheme
  Row(
    modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).semantics { selectableGroup() },
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
  ) {
    labels.forEachIndexed { index, label ->
      ToggleButton(
        checked = index == selected,
        onCheckedChange = {
          focus.clearFocus(force = true)
          onSelect(index)
        },
        modifier = Modifier.weight(1f).semantics { role = Role.Tab },
        shapes = when (index) {
          0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
          labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
          else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
        },
        colors = ToggleButtonDefaults.toggleButtonColors(
          containerColor = colors.surfaceContainerHigh,
          contentColor = colors.onSurfaceVariant,
          checkedContainerColor = colors.secondaryContainer,
          checkedContentColor = colors.onSecondaryContainer,
        ),
      ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}
