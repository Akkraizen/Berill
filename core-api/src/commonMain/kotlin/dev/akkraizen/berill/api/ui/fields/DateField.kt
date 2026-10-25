package dev.akkraizen.berill.api.ui.fields

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.akkraizen.berill.api.ui.*

private fun normalizeDate(text: String): String = text.toDateOrNull()?.toString() ?: text.trim()

@Composable
fun DateField(
  value: String?,
  onCommit: (String?) -> Unit,
  label: String,
  modifier: Modifier = Modifier
) {
  CommitTextField(
    value = value ?: "",
    onCommit = { onCommit(it.toDateOrNull()?.toString()) },
    label = label,
    placeholder = UiText.datePlaceholder(),
    isValid = { it.isBlank() || (it.trim().length == 10 && it.toDateOrNull() != null) },
    normalize = ::normalizeDate,
    modifier = modifier
  )
}
