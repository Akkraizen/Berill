package dev.akkraizen.berill.api.ui.fields

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

@OptIn(FlowPreview::class)
@Composable
fun rememberCommitState(
  value: String,
  normalize: (String) -> String = { it },
  onCommit: (String) -> Unit
): CommitState {
  val state = remember { CommitState(value) }
  val commit by rememberUpdatedState(onCommit)
  val norm by rememberUpdatedState(normalize)
  LaunchedEffect(value) {
    if (value != state.committed && value != norm(state.committed)) {
      state.committed = value
      state.text = value
    }
  }
  LaunchedEffect(state) {
    snapshotFlow { state.text }
      .drop(1)
      .debounce(400.milliseconds)
      .collect {
        if (it != state.committed) {
          state.committed = it
          commit(it)
        }
      }
  }
  DisposableEffect(state) {
    onDispose {
      if (state.text != state.committed) {
        state.committed = state.text
        commit(state.text)
      }
    }
  }
  return state
}

@Composable
fun CommitTextField(
  value: String,
  onCommit: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  singleLine: Boolean = true,
  keyboardType: KeyboardType = KeyboardType.Text,
  isValid: (String) -> Boolean = { true },
  placeholder: String? = null,
  normalize: (String) -> String = { it.trim() }
) {
  val state = rememberCommitState(value, normalize) { if (isValid(it)) onCommit(it) }
  BerillTextField(
    value = state.text,
    onValueChange = { state.text = it },
    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    placeholder = if (placeholder != null) {
      { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    } else
      null,
    singleLine = singleLine,
    minLines = if (singleLine) 1 else 3,
    isError = !isValid(state.text),
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    modifier = modifier.fillMaxWidth()
  )
}
