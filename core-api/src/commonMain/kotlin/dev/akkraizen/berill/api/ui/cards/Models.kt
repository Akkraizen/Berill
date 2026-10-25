package dev.akkraizen.berill.api.ui.cards

import androidx.compose.ui.graphics.vector.ImageVector
import dev.akkraizen.berill.api.ui.feedback.Tone

data class Stat(
  val label: String,
  val value: String,
  val sub: String? = null,
  val tone: Tone = Tone.Normal,
  val icon: ImageVector? = null
)
