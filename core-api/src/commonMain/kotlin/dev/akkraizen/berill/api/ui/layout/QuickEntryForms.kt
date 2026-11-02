package dev.akkraizen.berill.api.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.fields.DateTextField
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.api.ui.state.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer

@Composable
fun QuickDateField(value: String, onChange: (String) -> Unit) {
  Column {
    DateTextField(value, onChange, I18n.text("quick.date"), isError = value.toDateOrNull() == null)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TextButton(onClick = { onChange(today().toString()) }) { Text(I18n.text("quick.today")) }
      TextButton(onClick = { onChange(today().minus(1, DateTimeUnit.DAY).toString()) }) { Text(I18n.text("quick.yesterday")) }
    }
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QuickSaveButtons(valid: Boolean, onDismiss: () -> Unit, onSave: (again: Boolean) -> Unit) {
  FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    TextButton(onClick = onDismiss) { Text(I18n.text("ui.cancel")) }
    TextButton(enabled = valid, onClick = { onSave(true) }) { Text(I18n.text("quick.saveMore")) }
    FilledTonalButton(enabled = valid, onClick = { onSave(false) }) { Text(I18n.text("ui.save")) }
  }
}

@Composable
fun QuickForm(content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun <T> QuickPresets(kind: QuickEntryKind, serializer: KSerializer<T>, current: T, valid: Boolean, recent: List<T>, label: (T) -> String, onUse: (T) -> Unit) {
  val entries = LocalQuickEntries.current
  val revision = entries.revision
  val templates = remember(entries, kind, revision) { entries.templates(kind, serializer) }
  var selected by remember { mutableStateOf<T?>(null) }
  if (templates.isNotEmpty()) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      DropdownField(I18n.text("quick.templates"), templates, selected?.takeIf { it in templates }, { label(it).take(80) }, {
        selected = it; onUse(it)
      }, Modifier.weight(1f))
      TextButton(enabled = selected != null, onClick = { selected?.let { entries.removeTemplate(kind, serializer, it) }; selected = null }) { Text(I18n.text("quick.removeTemplate")) }
    }
  }
  if (recent.isNotEmpty()) DropdownField(I18n.text("quick.recent"), recent.distinct(), null, { label(it).take(80) }, onUse, Modifier.fillMaxWidth())
  TextButton(enabled = valid, onClick = { entries.addTemplate(kind, serializer, current); selected = current }) { Text(I18n.text("quick.saveTemplate")) }
}

@Composable
fun QuickEntryFeedback(modifier: Modifier = Modifier) {
  val entries = LocalQuickEntries.current
  val saved = entries.saved
  val host = remember { SnackbarHostState() }
  val earlier = remember(entries) { entries.saved?.id }
  LaunchedEffect(saved?.id) {
    val token = saved?.takeIf { it.id != earlier } ?: return@LaunchedEffect
    val result = host.showSnackbar(I18n.text("quick.saved"), I18n.text("quick.undo"), withDismissAction = true, duration = SnackbarDuration.Long)
    try {
      if (result == SnackbarResult.ActionPerformed) {
        if (!entries.undo(token)) {
          host.showSnackbar(I18n.text("quick.changed"))
          entries.dismiss(token)
        }
      } else entries.dismiss(token)
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) { entries.onError(e) }
  }
  SnackbarHost(host, modifier)
}

@Composable
fun QuickDraftActions(onClear: () -> Unit) {
  Text(I18n.text("quick.keepDraft"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  TextButton(onClick = onClear) { Text(I18n.text("quick.clearDraft")) }
}
