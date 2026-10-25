package dev.akkraizen.berill.api.ui.feedback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.BerillMotion

@Composable
fun ProgressLine(
  fraction: Float,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary
) {
  val animated by
  animateFloatAsState(fraction.coerceIn(0f, 1f), BerillMotion.slowSpatial(), label = "progress")
  LinearProgressIndicator(
    progress = { animated },
    modifier = modifier.fillMaxWidth().height(6.dp),
    color = color
  )
}
