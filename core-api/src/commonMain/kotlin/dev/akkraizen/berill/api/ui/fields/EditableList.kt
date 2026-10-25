package dev.akkraizen.berill.api.ui.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.*
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.state.rememberDraft

@Composable
fun EditableList(
  items: List<String>,
  onChange: (List<String>) -> Unit,
  placeholder: String,
  onReset: (() -> Unit)? = null,
  draftKey: String? = null
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    items.forEach { item ->
      BerillChip(
        onClick = null,
        label = { Text(item) },
        trailingIcon = if (items.size > 1) {
          {
            Icon(
              BerillIcons.Close,
              contentDescription = UiText.deleteItem(item)(),
              modifier = Modifier.size(18.dp).interactive(CircleShape, bleed = 4.dp) {
                onChange(items - item)
              }
            )
          }
        } else
          null
      )
    }
  }
  var text by (if (draftKey != null) rememberDraft(draftKey) else remember { mutableStateOf("") })
  val value = text.trim()
  val canAdd = value.isNotEmpty() && items.none { it.equals(value, ignoreCase = true) }
  fun add() {
    if (!canAdd) return
    onChange(items + value)
    text = ""
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    BerillTextField(
      text,
      { text = it },
      placeholder = { Text(placeholder) },
      singleLine = true,
      modifier = Modifier.weight(1f),
      onSubmit = ::add
    )
    IconButton(enabled = canAdd, onClick = ::add) {
      Icon(BerillIcons.Add, contentDescription = UiText.add())
    }
  }
  if (onReset != null) TextButton(onClick = onReset) { Text(UiText.resetList()) }
}
