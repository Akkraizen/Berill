package dev.akkraizen.berill.api.ui.fields

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.*
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.interaction.trackTextInput

@Composable
fun BodyEditor(
  value: String,
  onCommit: (String) -> Unit,
  placeholder: String = UiText.bodyPlaceholder(),
  modifier: Modifier = Modifier
) {
  val state =
    rememberCommitState(value, normalize = { if (it.isBlank()) "" else it }, onCommit = onCommit)
  val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
  BasicTextField(
    value = state.text,
    onValueChange = { state.text = it },
    textStyle = style,
    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    modifier = modifier.fillMaxWidth().heightIn(min = 160.dp).trackTextInput(),
    decorationBox = { inner ->
      Box {
        if (state.text.isEmpty())
          Text(
            placeholder,
            style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
          )
        inner()
      }
    }
  )
}
