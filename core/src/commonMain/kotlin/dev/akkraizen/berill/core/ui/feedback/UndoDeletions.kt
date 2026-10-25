package dev.akkraizen.berill.core.ui.feedback

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.data.LocalStore

@Composable
internal fun UndoDeletions(store: LocalStore, modifier: Modifier = Modifier) {
  val pending by store.pendingDeletions.collectAsState()
  val host = remember { SnackbarHostState() }
  LaunchedEffect(pending) {
    if (pending.isEmpty()) return@LaunchedEffect
    val result = host.showSnackbar(
      message = I18n.text("undo.deleted", "count" to pending.size),
      actionLabel = I18n.text("undo.restore"),
      withDismissAction = true,
      duration = SnackbarDuration.Long
    )
    if (result == SnackbarResult.ActionPerformed) store.undoDeletions(pending) else store.dismissDeletions(pending)
  }
  SnackbarHost(host, modifier.safeDrawingPadding().padding(16.dp).widthIn(max = 640.dp))
}
