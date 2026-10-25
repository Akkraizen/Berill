package dev.akkraizen.berill.api.ui.display

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.*

@Composable
fun Stars(rating: Int?, max: Int = 10) {
  val r = (rating ?: 0).coerceIn(0, max)
  if (r > 0)
    Row {
      repeat(r) { Ic(BerillIcons.Star, size = 14.dp, tint = MaterialTheme.colorScheme.tertiary) }
    }
}
