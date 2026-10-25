package dev.akkraizen.berill.api.ui.fields

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.akkraizen.berill.api.ui.*
import dev.akkraizen.berill.api.ui.interaction.BerillTextField

@Composable
fun SearchField(
  value: String,
  onChange: (String) -> Unit,
  placeholder: String = UiText.search(),
  modifier: Modifier = Modifier
) {
  BerillTextField(
    value,
    onChange,
    placeholder = { Text(placeholder) },
    singleLine = true,
    shape = MaterialTheme.shapes.extraLarge,
    modifier = modifier.fillMaxWidth()
  )
}
