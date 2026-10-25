package dev.akkraizen.berill.api.ui.fields

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.akkraizen.berill.api.ui.*

@Composable
fun IntField(value: Int?, onCommit: (Int?) -> Unit, label: String, modifier: Modifier = Modifier) {
  CommitTextField(
    value = value?.toString() ?: "",
    onCommit = { onCommit(it.trim().toIntOrNull()) },
    label = label,
    keyboardType = KeyboardType.Number,
    isValid = { it.isBlank() || it.trim().toIntOrNull() != null },
    normalize = { it.trim().toIntOrNull()?.toString() ?: it.trim() },
    modifier = modifier
  )
}

private fun formatNumber(value: Double): String =
  if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
fun DoubleField(
  value: Double?,
  onCommit: (Double?) -> Unit,
  label: String,
  modifier: Modifier = Modifier
) {
  CommitTextField(
    value = value?.let(::formatNumber) ?: "",
    onCommit = { onCommit(parseAmount(it)) },
    label = label,
    keyboardType = KeyboardType.Decimal,
    isValid = { it.isBlank() || parseAmount(it) != null },
    normalize = { s -> parseAmount(s)?.let(::formatNumber) ?: s.trim() },
    modifier = modifier
  )
}
