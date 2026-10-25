package dev.akkraizen.berill.core.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.interaction.interactive

@Composable
internal fun PluginTile(plugin: BerillPlugin, on: Boolean, onToggle: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val container by
  animateColorAsState(
    if (on) colors.primaryContainer else colors.surfaceContainerHigh,
    BerillMotion.effects(),
    label = "tile-bg"
  )
  val content by
  animateColorAsState(
    if (on) colors.onPrimaryContainer else colors.onSurface,
    BerillMotion.effects(),
    label = "tile-fg"
  )
  val iconBg by
  animateColorAsState(
    if (on) colors.primary else colors.surfaceContainerHighest,
    BerillMotion.effects(),
    label = "tile-icon-bg"
  )
  val iconFg by
  animateColorAsState(
    if (on) colors.onPrimary else colors.onSurfaceVariant,
    BerillMotion.effects(),
    label = "tile-icon-fg"
  )
  val scale by
  animateFloatAsState(
    if (on) 1f else 0.97f,
    spring(dampingRatio = 0.5f, stiffness = 500f),
    label = "tile-scale"
  )
  val shape = MaterialTheme.shapes.large
  Surface(
    color = container,
    contentColor = content,
    shape = shape,
    modifier = Modifier.fillMaxWidth()
      .height(TILE_HEIGHT)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .interactive(shape, onClick = onToggle)
  ) {
    Row(
      Modifier.fillMaxSize().padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        Modifier.size(40.dp).clip(CircleShape).background(iconBg),
        contentAlignment = Alignment.Center
      ) {
        Icon(plugin.icon, contentDescription = null, tint = iconFg, modifier = Modifier.size(22.dp))
      }
      Column(Modifier.weight(1f)) {
        Text(
          plugin.title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (plugin.description.isNotBlank())
          Text(
            plugin.description,
            style = MaterialTheme.typography.bodySmall,
            color = content.copy(alpha = 0.75f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
      }
      AnimatedVisibility(
        visible = on,
        enter = scaleIn(BerillMotion.fastSpatial()) + fadeIn(),
        exit = scaleOut() + fadeOut()
      ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.primary)
      }
    }
  }
}

private val TILE_HEIGHT = 92.dp
