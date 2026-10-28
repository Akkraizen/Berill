package dev.akkraizen.berill.core.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MenuOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.interaction.mouseScrollable
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
        if (entry.children.isNotEmpty())
          AnimatedVisibility(
            visible = entry.expanded,
            enter = fadeIn(BerillMotion.slowEffects()) + expandVertically(BerillMotion.spatial()),
            exit = fadeOut(BerillMotion.effects()) + shrinkVertically(BerillMotion.spatial()),
            label = "drawer-pages"
          ) {
            Column { entry.children.forEach { DrawerItem(it, onPicked, nested = true) } }
          }
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
    badge = if (entry.children.isNotEmpty()) {
      { ExpandChevron(entry.expanded) }
    } else
      null,
    onClick = {
      if (entry.children.isNotEmpty()) {
        entry.onToggle()
      } else {
        entry.onClick()
        onPicked()
      }
    },
    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
      .then(if (nested) Modifier.padding(start = 24.dp).height(44.dp) else Modifier)
  )
}

@Composable
internal fun ExpandChevron(expanded: Boolean, tint: Color = LocalContentColor.current) {
  val rotation by animateFloatAsState(if (expanded) 180f else 0f, BerillMotion.spatial(), label = "chevron")
  Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp).rotate(rotation))
}
