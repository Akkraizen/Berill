package dev.akkraizen.berill.core.connections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.akkraizen.berill.api.DayEntry
import dev.akkraizen.berill.api.RawRecord
import dev.akkraizen.berill.api.label
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.notes.*
import dev.akkraizen.berill.core.widgets.MOOD_EMOJI
import dev.akkraizen.berill.core.widgets.journalMood
import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.coroutines.CancellationException
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.log.AppLog
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.datetime.*

@Composable
internal fun ConnectionPanel(title: String, icon: ImageVector, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  val colors = MaterialTheme.colorScheme
  Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.padding(16.dp).widthIn(max = 720.dp).fillMaxWidth().heightIn(max = 720.dp),
      shape = RoundedCornerShape(32.dp), color = colors.surfaceContainer) {
      Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Surface(shape = RoundedCornerShape(16.dp), color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(26.dp)) }
          }
          Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
          IconButton(onClick = close, colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onSurfaceVariant)) {
            Icon(Icons.Outlined.Close, ConnectionsText.close())
          }
        }
        content()
      }
    }
  }
}

@Composable
internal fun TrashDialog(core: BerillCore, close: () -> Unit) {
  var deleted by remember { mutableStateOf(emptyList<SyncRecord>()) }
  var revision by remember { mutableIntStateOf(0) }
  var loadError by remember { mutableStateOf<String?>(null) }
  var pending by remember { mutableStateOf<List<SyncRecord>?>(null) }
  var clearAll by remember { mutableStateOf(false) }
  LaunchedEffect(revision) {
    core.store.changes.onStart { emit(Unit) }.collect {
      try { deleted = core.store.deletedRecords(); loadError = null }
      catch (e: CancellationException) { throw e } catch (e: Exception) { AppLog.error("trash", "load", e); loadError = failureMessage(e) }
    }
  }
  val colors = MaterialTheme.colorScheme
  ConnectionPanel(ConnectionsText.trash(), Icons.Outlined.DeleteOutline, close) {
    loadError?.let { FeatureErrorNotice(it) { revision++ } }
    if (deleted.isEmpty() && loadError == null) {
      Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(48.dp), tint = colors.onSurfaceVariant)
        Text(ConnectionsText.trashEmpty(), style = MaterialTheme.typography.titleMedium)
        Text(ConnectionsText.trashEmptyHint(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
      }
    } else {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(deleted.size.toString(), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        TextButton(onClick = { clearAll = true; pending = deleted.toList() },
          colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) {
          Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp)); Text(ConnectionsText.clearTrash())
        }
      }
      LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(deleted, key = { it.collection + "/" + it.id }) { record ->
          Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh) {
            BoxWithConstraints {
              val compact = maxWidth < 480.dp
              Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(RawRecord(record.id, record.fields).label(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                  Text(core.collections.owner(record.collection)?.title ?: ConnectionsText.notes(),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                val remove = { clearAll = false; pending = listOf(record) }
                val restore: () -> Unit = { core.connections.errors.run(FailureOperation.RESTORE) { core.store.restore(record.collection, record.id); deleted = core.store.deletedRecords() }; Unit }
                if (compact) {
                  IconButton(onClick = remove, colors = IconButtonDefaults.iconButtonColors(contentColor = colors.error)) {
                    Icon(Icons.Outlined.DeleteForever, ConnectionsText.deleteForever(), Modifier.size(20.dp))
                  }
                  FilledTonalIconButton(onClick = restore) { Icon(Icons.Outlined.Restore, ConnectionsText.restore(), Modifier.size(20.dp)) }
                } else {
                  TextButton(onClick = remove, colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) {
                    Icon(Icons.Outlined.DeleteForever, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(ConnectionsText.deleteForever())
                  }
                  FilledTonalButton(onClick = restore) {
                    Icon(Icons.Outlined.Restore, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(ConnectionsText.restore())
                  }
                }
              }
            }
          }
        }
      }
    }
  }
  pending?.let { batch ->
    AlertDialog(onDismissRequest = { pending = null }, icon = { Icon(Icons.Outlined.DeleteForever, null, tint = colors.error) },
      title = { Text(if (clearAll) ConnectionsText.clearTrashTitle() else ConnectionsText.deleteTrashTitle()) },
      text = { Text(if (clearAll) ConnectionsText.clearTrashBody() else ConnectionsText.deleteTrashBody()) },
      confirmButton = { TextButton(onClick = { core.connections.errors.run(FailureOperation.DELETE) { core.store.purgeDeleted(batch); deleted = core.store.deletedRecords(); pending = null } },
        colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) { Text(if (clearAll) ConnectionsText.clearTrash() else ConnectionsText.deleteForever()) } },
      dismissButton = { TextButton(onClick = { pending = null }) { Text(ConnectionsText.cancel()) } })
  }
}

@Composable
internal fun DayDialog(core: BerillCore, close: () -> Unit) {
  var date by remember { mutableStateOf(today()) }
  var entries by remember { mutableStateOf(emptyList<DayEntry>()) }
  var revision by remember { mutableIntStateOf(0) }
  var loadError by remember { mutableStateOf<String?>(null) }
  val partial by core.connections.dayPartial.collectAsState()
  val enabledPlugins by core.plugins.enabled.collectAsState()
  LaunchedEffect(date, revision, enabledPlugins) {
    entries = emptyList(); loadError = null
    core.store.changes.onStart { emit(Unit) }.collect {
      try { entries = core.connections.day(date); loadError = null }
      catch (e: CancellationException) { throw e } catch (e: Exception) { AppLog.error("day", "load", e); loadError = failureMessage(e) }
    }
  }
  val noteId = core.dailyNotes.id(date)
  val notes by remember { core.notes.observe().catch { error ->
    if (error is CancellationException) throw error
    if (error is Exception) core.connections.errors.report(FailureOperation.DAY, error) else throw error
    emit(emptyList())
  } }.collectAsState(emptyList())
  val colors = MaterialTheme.colorScheme
  ConnectionPanel(ConnectionsText.day(), Icons.Outlined.Today, close) {
    if (loadError != null || partial) FeatureErrorNotice(loadError ?: I18n.text("errors.partial")) { revision++ }
    Surface(shape = RoundedCornerShape(24.dp), color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
      Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { date = date.minus(1, DateTimeUnit.DAY) }) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, ConnectionsText.previousDay(), tint = colors.onSecondaryContainer)
        }
        TextButton(onClick = { date = today() }, modifier = Modifier.weight(1f),
          colors = ButtonDefaults.textButtonColors(contentColor = colors.onSecondaryContainer)) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayMonth(), style = MaterialTheme.typography.titleMedium)
            Text(if (date == today()) ConnectionsText.today() else date.year.toString(), style = MaterialTheme.typography.labelSmall)
          }
        }
        IconButton(onClick = { date = date.plus(1, DateTimeUnit.DAY) }) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, ConnectionsText.nextDay(), tint = colors.onSecondaryContainer)
        }
      }
    }
    FilledTonalButton(onClick = {
      if (core.connections.errors.run(FailureOperation.COMMAND) {
        core.nav.openNote(core.dailyNotes.ensure(date))
      }) close()
    }, modifier = Modifier.fillMaxWidth()) {
      Icon(Icons.Outlined.EditNote, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text(ConnectionsText.dayNote())
    }
    val mood = notes.firstOrNull { it.id == noteId }?.value?.body?.let(::journalMood)
    MoodRow(mood) { value -> core.connections.errors.run(FailureOperation.COMMAND) { core.dailyNotes.setMood(date, value) } }
    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      notes.firstOrNull { it.id == noteId }?.value?.body?.takeIf { it.isNotBlank() }?.let { body ->
        item { Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh) {
          Text(body.take(400), Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        } }
      }
      items(entries, key = { it.id }) { entry ->
        Surface(onClick = { if (core.connections.errors.run(FailureOperation.COMMAND) { entry.open() }) close() }, shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh) {
          Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EventNote, null, Modifier.size(24.dp), tint = colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(entry.title, style = MaterialTheme.typography.titleSmall)
              Text(entry.detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
          }
        }
      }
      if (entries.isEmpty() && loadError == null && !partial) item {
        Text(ConnectionsText.dayEmpty(), Modifier.fillMaxWidth().padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
      }
    }
  }
}

/** Mood of the selected day, stored as the first line of its daily note (like the journal widget). */
@Composable
private fun MoodRow(selected: Int?, onSelect: (Int) -> Unit) {
  val labels = dev.akkraizen.berill.core.widgets.WidgetText.moods
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(ConnectionsText.mood(), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
    MOOD_EMOJI.forEachIndexed { index, emoji ->
      FilterChip(
        selected = selected == index,
        onClick = { onSelect(index) },
        label = { Text(emoji, style = MaterialTheme.typography.titleMedium) },
        modifier = Modifier.semantics { contentDescription = labels.getOrNull(index).orEmpty() },
      )
    }
  }
}
