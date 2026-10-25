package dev.akkraizen.berill.api.ui.feedback

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(vertical = 6.dp)
  )
}
