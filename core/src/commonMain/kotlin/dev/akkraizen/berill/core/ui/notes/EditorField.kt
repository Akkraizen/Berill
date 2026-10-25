package dev.akkraizen.berill.core.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import dev.akkraizen.berill.api.ui.interaction.trackTextInput
import dev.akkraizen.berill.core.notes.*

@Composable
internal fun EditorField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  style: TextStyle,
  modifier: Modifier
) {
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    textStyle = style,
    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    modifier = modifier.trackTextInput(),
    decorationBox = { inner ->
      Box {
        if (value.isEmpty())
          Text(
            placeholder,
            style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
          )
        inner()
      }
    }
  )
}
