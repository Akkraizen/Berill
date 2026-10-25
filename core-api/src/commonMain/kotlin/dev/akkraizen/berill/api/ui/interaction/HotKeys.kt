package dev.akkraizen.berill.api.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

fun Modifier.trackTextInput(): Modifier =
  composed {
    val token = remember { Any() }
    DisposableEffect(token) { onDispose { HotKeys.setInputFocused(token, false) } }
    onFocusChanged { HotKeys.setInputFocused(token, it.isFocused || it.hasFocus) }
  }

@Composable
fun PrimaryAction(
  enabled: Boolean = true,
  action: () -> Unit
) = HotKeys.Register(primaryList = true, enabled = enabled, action = action)

@Composable
fun EscapeAction(
  enabled: Boolean = true,
  action: () -> Unit
) = HotKeys.Register(primaryList = false, enabled = enabled, action = action)

@Composable
fun DialogKeys(onConfirm: (() -> Unit)?, confirmEnabled: Boolean = true, onDismiss: () -> Unit) {
  if (onConfirm != null) PrimaryAction(confirmEnabled, onConfirm)
  EscapeAction(action = onDismiss)
}

object HotKeys {
  private val focusedInputs = mutableSetOf<Any>()

  val isTextInputFocused: Boolean
    get() = focusedInputs.isNotEmpty()

  internal fun setInputFocused(token: Any, focused: Boolean) {
    if (focused) focusedInputs += token else focusedInputs -= token
  }

  private class Entry(val enabled: () -> Boolean, val action: () -> Unit)

  private val primary = mutableListOf<Entry>()
  private val escape = mutableListOf<Entry>()

  fun runPrimary(): Boolean = run(primary)

  fun runEscape(): Boolean = run(escape)

  private fun run(list: MutableList<Entry>): Boolean {
    val entry = list.lastOrNull() ?: return false
    if (!entry.enabled()) return true
    entry.action()
    return true
  }

  @Composable
  internal fun Register(primaryList: Boolean, enabled: Boolean, action: () -> Unit) {
    val currentAction by rememberUpdatedState(action)
    val currentEnabled by rememberUpdatedState(enabled)
    DisposableEffect(Unit) {
      val entry = Entry({ currentEnabled }, { currentAction() })
      val list = if (primaryList) primary else escape
      list += entry
      onDispose { list.remove(entry) }
    }
  }
}
