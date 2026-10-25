package dev.akkraizen.berill.api.ui.feedback

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

enum class Tone {
  Normal,
  Muted,
  Good,
  Bad,
  Warn,
  Info
}

@Composable
fun Tone.color(): Color {
  val c = MaterialTheme.colorScheme
  val dark = c.surface.luminance() < 0.5f
  return when (this) {
    Tone.Normal -> c.onSurface
    Tone.Muted -> c.onSurfaceVariant
    Tone.Good -> c.primary
    Tone.Bad -> c.error
    Tone.Warn -> if (dark) Color(0xFFFFB74D) else Color(0xFFC25E00)
    Tone.Info -> c.tertiary
  }
}
