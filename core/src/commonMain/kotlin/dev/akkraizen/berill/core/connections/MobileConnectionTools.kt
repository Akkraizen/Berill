package dev.akkraizen.berill.core.connections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.sync.SyncState
import dev.akkraizen.berill.core.ui.CoreText

/** Two touch targets leave room for the page title on phones. */
@Composable
internal fun MobileConnectionTools(core: BerillCore, showSearch: Boolean, unread: Int) {
  var expanded by remember { mutableStateOf(false) }
  val state by core.sync.state.collectAsState()
  val focus = LocalFocusManager.current
  fun pick(action: () -> Unit) { expanded = false; focus.clearFocus(force = true); action() }
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (showSearch) HeaderAction(Icons.Outlined.Search, ConnectionsText.searchTitle(), compact = false, emphasized = true) { core.connections.searchOpen.value = true }
    Box(Modifier.padding(end = 4.dp)) {
      BadgedBox(badge = { if (unread > 0) Badge() }) {
        HeaderAction(Icons.Outlined.MoreVert, if (unread > 0) "${CoreText.menu()} · ${I18n.text("center.title")}: $unread" else CoreText.menu(), compact = false) { expanded = true }
      }
      DropdownMenu(expanded, { expanded = false }, Modifier.widthIn(min = 240.dp, max = 300.dp).heightIn(max = 400.dp), shape = RoundedCornerShape(24.dp)) {
        DropdownMenuItem(text = {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(I18n.text("center.title"), Modifier.weight(1f))
            if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) }
          }
        }, leadingIcon = { Icon(Icons.Outlined.Notifications, null) }, onClick = { pick { core.notifications.open.value = true } })
        DropdownMenuItem(text = { Text(ConnectionsText.day()) }, leadingIcon = { Icon(Icons.Outlined.Today, null) }, onClick = { pick { core.connections.dayOpen.value = true } })
        DropdownMenuItem(text = { Text(ConnectionsText.trash()) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) }, onClick = { pick { core.connections.trashOpen.value = true } })
        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        DropdownMenuItem(text = {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(CoreText.sync())
            Text(syncStatusDescription(state), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }, leadingIcon = {
          if (state == SyncState.Syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
          else Icon(syncStatusIcon(state), null, tint = if (state is SyncState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }, onClick = { pick { core.nav.openSettings("sync") } })
      }
    }
  }
}
