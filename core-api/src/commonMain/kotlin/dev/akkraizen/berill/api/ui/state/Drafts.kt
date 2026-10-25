package dev.akkraizen.berill.api.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember

@Composable
fun rememberDraft(
  key: String,
  initial: String = ""
): MutableState<String> = remember(key, initial) { DraftState(key, initial) }

private class DraftState(private val key: String, private val initial: String) : MutableState<String> {
  override var value: String
    get() = Drafts.get(key) ?: initial
    set(v) = Drafts.set(key, v.takeIf { it != initial })

  override fun component1(): String = value

  override fun component2(): (String) -> Unit = { value = it }
}

object Drafts {
  private val values = mutableStateMapOf<String, String>()

  fun get(key: String): String? = values[key]

  fun set(key: String, value: String?) {
    if (value == null) values.remove(key) else values[key] = value
  }

  fun clear(key: String) {
    values.remove(key)
  }
}
