package dev.akkraizen.berill.api.ui.fields

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.toDateOrNull

private fun normalizeDate(text: String): String = text.toDateOrNull()?.toString() ?: text.trim()

private fun validDate(text: String): Boolean = text.isBlank() || (text.trim().length == 10 && text.toDateOrNull() != null)

@Composable
fun DateField(
  value: String?,
  onCommit: (String?) -> Unit,
  label: String,
  modifier: Modifier = Modifier
) {
  val state = rememberCommitState(value ?: "", ::normalizeDate) { if (validDate(it)) onCommit(it.toDateOrNull()?.toString()) }
  BerillTextField(
    value = state.text,
    onValueChange = { state.text = it },
    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    placeholder = { Text(UiText.datePlaceholder(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
    trailingIcon = { DatePickerButton(state.text) { state.text = it } },
    singleLine = true,
    isError = !validDate(state.text),
    modifier = modifier.fillMaxWidth()
  )
}

@Composable
fun TimeField(
  value: String,
  onChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  isError: Boolean = parseClock(value) == null,
  supportingText: String? = null,
  onSubmit: (() -> Unit)? = null
) {
  BerillTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    placeholder = { Text("09:00", maxLines = 1) },
    trailingIcon = { TimePickerButton(value, onPick = onChange) },
    singleLine = true,
    isError = isError,
    supportingText = supportingText?.let { { Text(it) } },
    modifier = modifier.fillMaxWidth(),
    onSubmit = onSubmit
  )
}

@Composable
fun DateTextField(
  value: String,
  onChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  isError: Boolean = value.isNotBlank() && value.toDateOrNull() == null,
  placeholder: String = UiText.datePlaceholder(),
  onSubmit: (() -> Unit)? = null
) {
  BerillTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    trailingIcon = { DatePickerButton(value, onPick = onChange) },
    singleLine = true,
    isError = isError,
    modifier = modifier.fillMaxWidth(),
    onSubmit = onSubmit
  )
}
