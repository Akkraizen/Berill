package dev.akkraizen.berill.core.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.AppearAnimated
import dev.akkraizen.berill.api.ui.animation.BerillMotion

@Composable
internal fun StepHeader(title: String, text: String) {
  AppearAnimated {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.headlineMedium)
      Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
internal fun StepIndicator(step: Int, total: Int, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    repeat(total) { i ->
      val width by
      animateDpAsState(
        if (i == step) 32.dp else 10.dp,
        BerillMotion.spatial(),
        label = "step-width"
      )
      val color by
      animateColorAsState(
        if (i <= step) colors.primary else colors.outlineVariant,
        BerillMotion.effects(),
        label = "step-color"
      )
      Box(Modifier.width(width).height(8.dp).clip(CircleShape).background(color))
    }
  }
}

@Composable
internal fun AnimatedBackdrop(modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val drift = rememberInfiniteTransition(label = "backdrop")
  val a by
  drift.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
    label = "backdrop-drift"
  )
  val first = colors.primary.copy(alpha = 0.16f)
  val second = colors.tertiary.copy(alpha = 0.14f)
  Canvas(modifier) {
    val r = maxOf(size.width, size.height) * 0.45f
    val c1 = Offset(size.width * (0.12f + 0.2f * a), size.height * (0.18f + 0.1f * a))
    drawCircle(
      Brush.radialGradient(listOf(first, Color.Transparent), center = c1, radius = r),
      radius = r,
      center = c1
    )
    val c2 = Offset(size.width * (0.88f - 0.2f * a), size.height * (0.85f - 0.15f * a))
    drawCircle(
      Brush.radialGradient(listOf(second, Color.Transparent), center = c2, radius = r),
      radius = r,
      center = c2
    )
  }
}
