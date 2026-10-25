package dev.akkraizen.berill.api.ui.fields

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class CommitState internal constructor(initial: String) {
  var text by mutableStateOf(initial)
  internal var committed: String = initial
}

fun normalizeCommaList(text: String): String =
  text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
