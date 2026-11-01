package dev.akkraizen.berill.core.notes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Flushes the currently open editor before an external writer performs its guarded mutation. */
class EditorSessions {
  private val current = MutableStateFlow<(() -> Unit)?>(null)
  fun attach(flush: () -> Unit): () -> Unit {
    current.value = flush
    return { try { flush() } finally { if (current.value === flush) current.value = null } }
  }
  fun flushNow() { current.value?.invoke() }
  suspend fun prepareMutation() {
    if (current.value == null) return
    withContext(Dispatchers.Main) { flushNow() }
  }
}
