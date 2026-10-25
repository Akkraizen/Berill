package dev.akkraizen.berill.core.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun MosaicLogo(size: Dp) {
  val colors = MaterialTheme.colorScheme
  val palette = listOf(
    colors.primary,
    colors.tertiary,
    colors.secondary,
    colors.tertiaryContainer,
    colors.primary,
    colors.secondaryContainer,
    colors.primaryContainer,
    colors.secondary,
    colors.tertiary
  )
  val breathing = rememberInfiniteTransition(label = "mosaic")
  val wave by
  breathing.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      tween(2600, easing = FastOutSlowInEasing),
      RepeatMode.Reverse
    ),
    label = "mosaic-wave"
  )
  val gap = size / 18
  val tile = (size - gap * 2) / 3
  Column(verticalArrangement = Arrangement.spacedBy(gap)) {
    for (row in 0 until 3) {
      Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (col in 0 until 3) {
          val i = row * 3 + col
          val appear = remember { Animatable(0f) }
          LaunchedEffect(Unit) {
            delay(70L * i)
            appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
          }
          val direction = if ((row + col) % 2 == 0) 1f else -1f
          Box(
            Modifier.size(tile)
              .graphicsLayer {
                val p = appear.value
                scaleX = p
                scaleY = p
                alpha = p.coerceIn(0f, 1f)
                rotationZ = (1f - p) * -45f
                translationY = (wave - 0.5f) * 6.dp.toPx() * direction
              }
              .clip(if (i == 4) CircleShape else RoundedCornerShape(tile / 4))
              .background(palette[i])
          )
        }
      }
    }
  }
}
