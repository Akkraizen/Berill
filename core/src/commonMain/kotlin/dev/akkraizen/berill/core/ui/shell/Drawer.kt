package dev.akkraizen.berill.core.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import dev.akkraizen.berill.api.ui.interaction.mouseScrollable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.core.ui.CoreText

@Composable
internal fun ColumnScope.DrawerContent(model: NavModel, onCollapse: (() -> Unit)?, onPicked: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      "Berill",
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.weight(1f)
    )
    if (onCollapse != null)
      IconButton(onClick = onCollapse) {
        Icon(Icons.Outlined.MenuOpen, CoreText.collapsePanel())
      }
  }
  Column(Modifier.weight(1f).mouseScrollable(rememberScrollState())) {
    model.top.forEach { DrawerItem(it, onPicked) }
    if (model.sections.isNotEmpty()) {
      Text(
        CoreText.sections(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
      )
      model.sections.forEach { entry ->
        DrawerItem(entry, onPicked)
        if (entry.open) entry.children.forEach { DrawerItem(it, onPicked, nested = true) }
      }
    }
  }
  HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
  model.bottom.forEach { DrawerItem(it, onPicked) }
  Spacer(Modifier.height(12.dp))
}

@Composable
private fun DrawerItem(entry: NavEntry, onPicked: () -> Unit, nested: Boolean = false) {
  NavigationDrawerItem(
    label = {
      Text(
        entry.title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = if (nested)
          MaterialTheme.typography.labelMedium
        else
          MaterialTheme.typography.labelLarge
      )
    },
    icon = {
      Icon(entry.icon, contentDescription = null, modifier = Modifier.size(if (nested) 20.dp else 24.dp))
    },
    selected = entry.selected,
    onClick = {
      entry.onClick()
      onPicked()
    },
    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
      .then(if (nested) Modifier.padding(start = 24.dp).height(44.dp) else Modifier)
  )
}
