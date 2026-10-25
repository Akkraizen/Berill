package dev.akkraizen.berill.desktop.ui.window

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import dev.akkraizen.berill.desktop.ui.BerillIcon

@Composable
fun FrameWindowScope.WindowTitleBar(title: String, state: WindowState, onClose: () -> Unit) {
  val maximized = state.placement == WindowPlacement.Maximized
  fun toggleMaximize() {
    if (state.placement == WindowPlacement.Maximized) {
      state.placement = WindowPlacement.Floating
    } else {
      fitMaximizedBoundsToScreen(window)
      state.placement = WindowPlacement.Maximized
    }
  }

  Row(
    Modifier.fillMaxWidth()
      .height(44.dp)
      .background(MaterialTheme.colorScheme.surface)
      .padding(start = 12.dp, end = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      Modifier.weight(1f)
        .fillMaxHeight()
        .windowDrag(window, state, onDoubleClick = ::toggleMaximize),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        Modifier.size(26.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          painter = BerillIcon,
          contentDescription = null,
          tint = Color.Unspecified,
          modifier = Modifier.size(16.dp)
        )
      }
      Text(
        "Berill",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
      )
      if (title != "Berill")
        Text(
          title,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      CaptionButton(Icons.Outlined.Remove, Tr("windowChrome.WindowTitleBar")()) {
        state.isMinimized = true
      }
      CaptionButton(
        if (maximized) Icons.Outlined.FilterNone else Icons.Outlined.CropSquare,
        if (maximized) Tr("windowChrome.WindowTitleBar.2")() else Tr("windowChrome.WindowTitleBar.3")(),
        onClick = ::toggleMaximize
      )
      CaptionButton(Icons.Outlined.Close, Tr("windowChrome.WindowTitleBar.4")(), danger = true, onClick = onClose)
    }
  }
}

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
