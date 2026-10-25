package dev.akkraizen.berill.core.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.core.ui.theme.ACCENT_PRESETS
import dev.akkraizen.berill.core.ui.theme.Appearance
import dev.akkraizen.berill.core.ui.theme.accentSwatch
import dev.akkraizen.berill.core.ui.theme.supportsDynamicColor

@Composable
internal fun AccentSwatches(selected: String, onSelect: (String) -> Unit) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    ACCENT_PRESETS.forEach { (name, accent) ->
      val color = accentSwatch(accent) ?: MaterialTheme.colorScheme.primary
      Swatch(
        name(),
        Brush.linearGradient(listOf(color, color)),
        color,
        selected.equals(accent, ignoreCase = true)
      ) {
        onSelect(accent)
      }
    }
    if (supportsDynamicColor) {
      val c = MaterialTheme.colorScheme
      Swatch(
        OnboardingText.wallpaper(),
        Brush.sweepGradient(listOf(c.primary, c.tertiary, c.secondary, c.primary)),
        c.primary,
        selected == Appearance.ACCENT_DYNAMIC,
        icon = Icons.Outlined.Wallpaper
      ) {
        onSelect(Appearance.ACCENT_DYNAMIC)
      }
    }
  }
}

@Composable
private fun Swatch(
  name: String,
  fill: Brush,
  base: Color,
  selected: Boolean,
  icon: ImageVector? = null,
  onClick: () -> Unit
) {
  val scale by
  animateFloatAsState(
    if (selected) 1.08f else 1f,
    BerillMotion.fastSpatial(),
    label = "swatch-scale"
  )
  val mark = if (base.luminance() > 0.5f) Color.Black else Color.White
  Column(
    Modifier.width(72.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Box(
      Modifier.size(52.dp)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .clip(CircleShape)
        .background(fill)
        .interactive(CircleShape, onClick = onClick),
      contentAlignment = Alignment.Center
    ) {
      if (icon != null && !selected) Icon(icon, contentDescription = null, tint = mark)
      androidx.compose.animation.AnimatedVisibility(
        visible = selected,
        enter = scaleIn(BerillMotion.fastSpatial()) + fadeIn(),
        exit = scaleOut() + fadeOut()
      ) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = mark)
      }
    }
    Text(
      name,
      style = MaterialTheme.typography.labelSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center
    )
  }
}
