package dev.akkraizen.berill.core.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.interaction.mouseScrollable
import dev.akkraizen.berill.core.ui.CoreText

private val SIDEBAR_EXPANDED = 280.dp

private val SIDEBAR_COLLAPSED = 88.dp

@Composable
internal fun AnimatedSidebar(model: NavModel, expanded: Boolean, onToggle: () -> Unit) {
  val width by
    animateDpAsState(
      if (expanded) SIDEBAR_EXPANDED else SIDEBAR_COLLAPSED,
      BerillMotion.spatial(),
      label = "sidebar-width"
    )
  Column(Modifier.width(width).fillMaxHeight().clipToBounds().padding(vertical = 8.dp)) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onToggle) {
        val rotation by
          animateFloatAsState(if (expanded) 0f else 180f, BerillMotion.spatial(), label = "toggle")
        Icon(
          Icons.Outlined.MenuOpen,
          contentDescription = if (expanded) CoreText.collapsePanel() else CoreText.expandPanel(),
          modifier = Modifier.rotate(rotation)
        )
      }
      SidebarLabel(expanded) {
        Text(
          "Berill",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          modifier = Modifier.padding(start = 8.dp)
        )
      }
    }
    Column(Modifier.weight(1f).mouseScrollable(rememberScrollState())) {
      model.top.forEach { SidebarItem(it, expanded) }
      if (model.sections.isNotEmpty()) {

        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterStart) {
          SidebarLabel(expanded) {
            Text(
              CoreText.sections(),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              modifier = Modifier.padding(start = 28.dp)
            )
          }
          SidebarLabel(!expanded) { HorizontalDivider(Modifier.padding(horizontal = 24.dp)) }
        }
        model.sections.forEach { entry ->
          SidebarItem(entry, expanded)

          if (entry.children.isNotEmpty()) {
            AnimatedVisibility(
              visible = entry.expanded && expanded,
              enter = fadeIn(BerillMotion.slowEffects()) + expandVertically(BerillMotion.spatial()),
              exit = fadeOut(BerillMotion.effects()) + shrinkVertically(BerillMotion.spatial()),
              label = "sidebar-pages"
            ) {
              Column { entry.children.forEach { SidebarSubItem(it) } }
            }
          }
        }
      }
    }
    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    model.bottom.forEach { SidebarItem(it, expanded) }
  }
}

@Composable
private fun SidebarLabel(visible: Boolean, content: @Composable () -> Unit) {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(BerillMotion.slowEffects()) +
      expandHorizontally(BerillMotion.spatial(), expandFrom = Alignment.Start),
    exit = fadeOut(BerillMotion.effects()) +
      shrinkHorizontally(BerillMotion.spatial(), shrinkTowards = Alignment.Start),
    label = "sidebar-label"
  ) {
    content()
  }
}

@Composable
private fun SidebarItem(entry: NavEntry, expanded: Boolean) {
  val colors = MaterialTheme.colorScheme

  val selected = entry.selected || (entry.open && entry.children.isNotEmpty() && (!expanded || !entry.expanded))
  val background by
    animateColorAsState(
      if (selected) colors.secondaryContainer else Color.Transparent,
      BerillMotion.effects(),
      label = "item-bg"
    )
  val content by
    animateColorAsState(
      when {
        selected -> colors.onSecondaryContainer
        entry.open -> colors.primary
        else -> colors.onSurfaceVariant
      },
      BerillMotion.effects(),
      label = "item-fg"
    )
  Row(
    Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
      .fillMaxWidth()
      .height(52.dp)
      .clip(CircleShape)
      .background(background)
      .interactive(CircleShape, onClick = if (expanded && entry.children.isNotEmpty()) entry.onToggle else entry.onClick)
      .padding(start = 20.dp, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(entry.icon, contentDescription = if (expanded) null else entry.title, tint = content)
    SidebarLabel(expanded) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          entry.title,
          color = content,
          style = MaterialTheme.typography.labelLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 12.dp).weight(1f)
        )
        if (entry.children.isNotEmpty()) ExpandChevron(entry.expanded, content)
      }
    }
  }
}

@Composable
private fun SidebarSubItem(entry: NavEntry) {
  val colors = MaterialTheme.colorScheme
  val background by
    animateColorAsState(
      if (entry.selected) colors.secondaryContainer else Color.Transparent,
      BerillMotion.effects(),
      label = "sub-bg"
    )
  val content by
    animateColorAsState(
      if (entry.selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
      BerillMotion.effects(),
      label = "sub-fg"
    )
  Row(
    Modifier.padding(start = 40.dp, end = 12.dp, top = 1.dp, bottom = 1.dp)
      .fillMaxWidth()
      .height(40.dp)
      .clip(CircleShape)
      .background(background)
      .interactive(CircleShape, onClick = entry.onClick)
      .padding(start = 16.dp, end = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(entry.icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
    Text(
      entry.title,
      color = content,
      style = MaterialTheme.typography.labelMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(start = 12.dp)
    )
  }
}
