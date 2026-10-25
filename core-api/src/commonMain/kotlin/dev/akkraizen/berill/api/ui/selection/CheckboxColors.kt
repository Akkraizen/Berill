package dev.akkraizen.berill.api.ui.selection

import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Composable
fun pastelCheckboxColors(): CheckboxColors {
  val fill = lerp(MaterialTheme.colorScheme.primary, Color.White, 0.35f)
  return CheckboxDefaults.colors(
    checkedColor = fill,
    checkmarkColor = if (fill.luminance() > 0.179f) Color.Black else Color.White
  )
}