package dev.akkraizen.berill.core.connections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import dev.akkraizen.berill.api.ui.hhmm
import dev.akkraizen.berill.api.ui.localDateTime
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.sync.SyncState
import dev.akkraizen.berill.core.ui.CoreText

/** Quiet, live status. Sync never increments the notification counter. */
@Composable
fun ConnectionSyncStatus(core: BerillCore) {
  val state by core.sync.state.collectAsState()
  HeaderAction(
    icon = syncStatusIcon(state),
    description = syncStatusDescription(state), compact = true, busy = state == SyncState.Syncing,
    tint = if (state is SyncState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
  ) { core.nav.openSettings("sync") }
}

internal fun syncStatusDescription(state: SyncState): String = when (state) {
  SyncState.NotConfigured -> CoreText.syncNotConfigured()
  SyncState.Idle -> CoreText.syncIdle()
  SyncState.Syncing -> CoreText.syncInProgress()
  is SyncState.Done -> CoreText.syncDone(localDateTime(state.atMillis).hhmm(), state.sent, state.received)()
  is SyncState.Failed -> CoreText.syncFailed(state.message)()
}
internal fun syncStatusIcon(state: SyncState) = when (state) {
  is SyncState.Done -> Icons.Outlined.CloudDone
  is SyncState.Failed, SyncState.NotConfigured -> Icons.Outlined.CloudOff
  else -> Icons.Outlined.CloudQueue
}
