package dev.akkraizen.berill.core.connections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.notes.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import dev.akkraizen.berill.api.i18n.I18n
import kotlinx.datetime.*
import kotlinx.serialization.json.*

@Composable
internal fun ConnectionDialogs(core: BerillCore) {
  val shared by core.connections.shared.collectAsState()
  shared?.let { ShareDialog(core, it) { core.connections.shared.value = null } }
  val search by core.connections.searchOpen.collectAsState()
  val day by core.connections.dayOpen.collectAsState()
  val trash by core.connections.trashOpen.collectAsState()
  if (search) SearchDialog(core) { core.connections.searchOpen.value = false }
  if (day) DayDialog(core) { core.connections.dayOpen.value = false }
  if (trash) TrashDialog(core) { core.connections.trashOpen.value = false }
  val notifications by core.notifications.open.collectAsState()
  val conflicts by core.notifications.conflictsOpen.collectAsState()
  if (notifications) NotificationDialog(core)
  if (conflicts) ConflictsDialog(core)
  FeatureErrorDialog(core.connections.errors)
}

@Composable
internal fun Backlinks(core: BerillCore, id: String, title: String) {
  val links by remember(id, title) { core.notes.backlinks(id, title).catch { error ->
    if (error is CancellationException) throw error
    if (error is Exception) core.connections.errors.report(FailureOperation.COMMAND, error) else throw error
    emit(emptyList())
  } }.collectAsState(emptyList())
  if (links.isNotEmpty()) dev.akkraizen.berill.api.ui.cards.SectionCard(ConnectionsText.backlinks(), Icons.Outlined.Link) {
    dev.akkraizen.berill.api.ui.layout.BoundedList {
      links.forEach { note -> ConnectionAction(note.value.displayTitle(), Icons.Outlined.Description) { core.connections.errors.run(FailureOperation.COMMAND) { core.nav.openNote(note.id) }; Unit } }
    }
  }
}

@Composable
private fun ShareDialog(core: BerillCore, content: SharedText, close: () -> Unit) {
  val plugins by core.plugins.enabled.collectAsState()
  var sources by remember { mutableStateOf(emptyList<ShareSource>()) }
  var partial by remember { mutableStateOf(false) }
  var revision by remember { mutableIntStateOf(0) }
  LaunchedEffect(plugins, revision) {
    var failed = false
    sources = plugins.flatMap { plugin ->
      tolerateSource(plugin.id, { failed = true }) { plugin.provides(core.plugins.context(plugin)).filterIsInstance<ShareSource>() }.orEmpty()
    }
    partial = failed
  }
  ConnectionPanel(ConnectionsText.shareTitle(), Icons.Outlined.Share, close) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
      Text(content.title.ifBlank { content.text.take(160) }, Modifier.fillMaxWidth().padding(16.dp),
        style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
    if (partial) FeatureErrorNotice(I18n.text("errors.partial")) { revision++ }
    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      item { ConnectionAction(ConnectionsText.notes(), Icons.Outlined.Description) {
        if (core.connections.errors.run(FailureOperation.SHARE) {
          val id = core.data.newId()
          core.notes.save(id, content.title, content.text); core.nav.showNote(id)
        }) close()
      } }
      sources.forEach { source -> item { ConnectionAction(source.label, Icons.Outlined.AddLink) { if (core.connections.errors.run(FailureOperation.SHARE) { source.accept(content) }) close() } } }
    }
  }
}
