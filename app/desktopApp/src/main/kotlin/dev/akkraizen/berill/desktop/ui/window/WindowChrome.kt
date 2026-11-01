package dev.akkraizen.berill.desktop.ui.window

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.desktop.DesktopOs
import dev.akkraizen.berill.desktop.ui.BerillIcon

@Composable
fun FrameWindowScope.WindowTitleBar(title: String, state: WindowState, onClose: () -> Unit, actions: @Composable () -> Unit = {}, search: @Composable (compact: Boolean) -> Unit = {}, status: @Composable () -> Unit = {}) {
  val maximized = state.placement == WindowPlacement.Maximized
  fun toggleMaximize() {
    if (maximized) state.placement = WindowPlacement.Floating
    else { fitMaximizedBoundsToScreen(window); state.placement = WindowPlacement.Maximized }
  }
  WindowToolbar(title, Modifier.windowDrag(window, state, onDoubleClick = ::toggleMaximize), DesktopOs.isMac, search, actions, status = status) {
    if (!DesktopOs.isMac) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      CaptionButton(Icons.Outlined.Remove, Tr("windowChrome.WindowTitleBar")()) { state.isMinimized = true }
      CaptionButton(if (maximized) Icons.Outlined.FilterNone else Icons.Outlined.CropSquare,
        if (maximized) Tr("windowChrome.WindowTitleBar.2")() else Tr("windowChrome.WindowTitleBar.3")(), onClick = ::toggleMaximize)
      CaptionButton(Icons.Outlined.Close, Tr("windowChrome.WindowTitleBar.4")(), danger = true, onClick = onClose)
    }
  }
}

/** The centered slot has equal room on both sides; narrow windows use a compact search target. */
@Composable
internal fun WindowToolbar(title: String, dragModifier: Modifier = Modifier, mac: Boolean = false,
  search: @Composable (Boolean) -> Unit, actions: @Composable () -> Unit, status: @Composable () -> Unit = {}, captionButtons: @Composable () -> Unit) {
  BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp).background(MaterialTheme.colorScheme.surface)) {
    val centered = maxWidth >= 840.dp
    // A dedicated background target retains dragging and double-click maximize between controls.
    Box(Modifier.matchParentSize().then(dragModifier))
    if (centered) {
      val side = if (mac) 220.dp else 300.dp
      val searchWidth = minOf(460.dp, maxWidth - side * 2)
      Box(Modifier.align(Alignment.CenterStart).padding(start = if (mac) MAC_TRAFFIC_LIGHTS_WIDTH else 16.dp)
        .width((maxWidth - searchWidth) / 2 - if (mac) 94.dp else 32.dp)) { WindowIdentity(title, status = status) }
      Box(Modifier.align(Alignment.Center).width(searchWidth)) { search(false) }
      Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        actions()
        if (!mac) VerticalDivider(Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        captionButtons()
      }
    } else Row(Modifier.fillMaxSize().padding(start = if (mac) MAC_TRAFFIC_LIGHTS_WIDTH else 12.dp, end = 6.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.weight(1f)) { WindowIdentity(title, compact = true, status = status) }
      search(true)
      actions()
      if (!mac) VerticalDivider(Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      captionButtons()
    }
  }
}

@Composable
private fun WindowIdentity(title: String, compact: Boolean = false, status: @Composable () -> Unit = {}) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
      Icon(painter = BerillIcon, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
    }
    status()
    Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      Text("Berill", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (!compact && title != "Berill") Text(title, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

private val MAC_TRAFFIC_LIGHTS_WIDTH = 78.dp

@Composable
private fun CaptionButton(icon: ImageVector, description: String, danger: Boolean = false, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val hovered by interaction.collectIsHoveredAsState()
  val pressed by interaction.collectIsPressedAsState()
  val colors = MaterialTheme.colorScheme
  val background by
    animateColorAsState(
      when {
        danger && (hovered || pressed) ->
          if (pressed) colors.error.copy(alpha = 0.85f) else colors.error

        pressed -> colors.onSurface.copy(alpha = 0.14f)
        hovered -> colors.onSurface.copy(alpha = 0.08f)
        else -> Color.Transparent
      }
    )
  val tint by
    animateColorAsState(if (danger && (hovered || pressed)) colors.onError else colors.onSurfaceVariant)
  Box(
    Modifier.size(width = 44.dp, height = 32.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(background)
      .hoverable(interaction)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
  }
}
