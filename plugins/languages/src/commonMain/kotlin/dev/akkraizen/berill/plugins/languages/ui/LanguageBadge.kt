package dev.akkraizen.berill.plugins.languages.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.plugins.languages.data.Language

@Composable
internal fun LanguageBadge(language: Language) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.small
  ) {
    Text(
      language.flag,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      modifier = Modifier.widthIn(min = 40.dp).padding(horizontal = 8.dp, vertical = 6.dp)
    )
  }
}
