package dev.akkraizen.berill.plugins.languages.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.SearchField
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.plugins.languages.data.LanguageSelection
import dev.akkraizen.berill.plugins.languages.data.PHRASES_SECTION
import dev.akkraizen.berill.plugins.languages.data.WORDS_SECTION
import dev.akkraizen.berill.plugins.languages.data.Word
import dev.akkraizen.berill.plugins.languages.data.byCode
import dev.akkraizen.berill.plugins.languages.data.rememberLanguages
import dev.akkraizen.berill.plugins.languages.data.selected
import dev.akkraizen.berill.plugins.languages.data.words
import dev.akkraizen.berill.plugins.languages.i18n.LanguagesText

@Composable
internal fun DictionaryScreen(ctx: PluginContext) {
  val languages = rememberLanguages(ctx)
  val lang = languages.selected()
  if (lang == null) {
    PluginList { item { EmptyHint(LanguagesText.emptyLanguages()) } }
    return
  }
  val collection = remember(ctx) { words(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  var query by rememberDraft("languages/dictionary/$lang/query")
  var section by rememberSaveable { mutableStateOf(WORDS_SECTION) }
  var editing by remember { mutableStateOf<Item<Word>?>(null) }
  var adding by remember { mutableStateOf(false) }
  val l = languages.byCode(lang)
  val q = query.trim().lowercase()
  val shown = all.filter { it.value.language == lang && it.value.section == section }
    .filter {
      q.isEmpty() ||
        it.value.word.lowercase().contains(q) ||
        it.value.translation.lowercase().contains(q)
    }
    .sortedBy { it.value.word.lowercase() }

  PluginList {
    item {
      DetailHeader(LanguagesText.dictionaryOf(l.name)(), DICT_ICON) {
        FilledTonalButton(onClick = { adding = true }) {
          Ic(BerillIcons.Add, size = 18.dp)
          Spacer(Modifier.width(8.dp))
          Text(LanguagesText.add())
        }
      }
    }
    if (languages.size > 1)
      item {
        ChoiceChips(
          languages.map { it.code },
          lang,
          { languages.byCode(it).let { x -> "${x.flag} ${x.name}" } },
          { LanguageSelection.code = it }
        )
      }
    item { SearchField(query, { query = it }, LanguagesText.searchPlaceholder()) }
    item {
      ChoiceChips(
        listOf(WORDS_SECTION, PHRASES_SECTION),
        section,
        { LanguagesText.sectionLabel(it) },
        { section = it })
    }
    if (shown.isEmpty()) item { EmptyHint(LanguagesText.nothingFound()) }
    shown.forEach { entry ->
      item(key = entry.id) {
        val w = entry.value
        Surface(
          onClick = { editing = entry },
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.fillMaxWidth().animateItem()
        ) {
          Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
              Text(w.word, style = MaterialTheme.typography.titleMedium)
              w.pron?.let {
                Text(
                  it,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Column(Modifier.weight(1f)) {
              Text(w.translation)
              val meta = listOfNotNull(w.pos, w.lesson).joinToString(" · ")
              if (meta.isNotEmpty())
                Text(
                  meta,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
          }
        }
      }
    }
  }

  editing?.let { item ->
    WordDialog(
      initial = item.value,
      languageName = l.name,
      onDismiss = { editing = null },
      onSave = {
        collection.save(item.id, it)
        editing = null
      },
      onDelete = {
        collection.delete(item.id)
        editing = null
      }
    )
  }
  if (adding)
    WordDialog(
      initial = Word(language = lang, section = section),
      languageName = l.name,
      onDismiss = { adding = false },
      onSave = {
        collection.save(ctx.data.newId(), it)
        adding = false
      },
      onDelete = null
    )
}

@Composable
private fun WordDialog(
  initial: Word,
  languageName: String,
  onDismiss: () -> Unit,
  onSave: (Word) -> Unit,
  onDelete: (() -> Unit)?
) {
  var word by remember { mutableStateOf(initial.word) }
  var pron by remember { mutableStateOf(initial.pron ?: "") }
  var translation by remember { mutableStateOf(initial.translation) }
  var pos by remember { mutableStateOf(initial.pos ?: "") }
  var lesson by remember { mutableStateOf(initial.lesson ?: "") }
  var section by remember { mutableStateOf(initial.section) }
  var askDelete by remember { mutableStateOf(false) }
  val confirm: () -> Unit = {
    onSave(
      initial.copy(
        section = section,
        word = word.trim(),
        pron = pron.trim().ifEmpty { null },
        translation = translation.trim(),
        pos = pos.trim().ifEmpty { null }.takeIf { section == WORDS_SECTION },
        lesson = lesson.trim().ifEmpty { null }
      )
    )
  }
  DialogKeys(
    onConfirm = confirm,
    confirmEnabled = word.isNotBlank() && translation.isNotBlank(),
    onDismiss = onDismiss
  )
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(if (onDelete == null) LanguagesText.newEntry() else LanguagesText.dictionaryEntry())
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChips(
          listOf(WORDS_SECTION, PHRASES_SECTION),
          section,
          { LanguagesText.sectionLabel(it) },
          { section = it }
        )
        BerillTextField(word, { word = it }, label = { Text(languageName) }, singleLine = true)
        BerillTextField(
          pron,
          { pron = it },
          label = { Text(LanguagesText.pronunciation()) },
          singleLine = true
        )
        BerillTextField(
          translation,
          { translation = it },
          label = { Text(LanguagesText.translation()) },
          singleLine = true
        )
        if (section == WORDS_SECTION)
          BerillTextField(
            pos,
            { pos = it },
            label = { Text(LanguagesText.partOfSpeech()) },
            singleLine = true
          )
        BerillTextField(
          lesson,
          { lesson = it },
          label = { Text(LanguagesText.lesson()) },
          singleLine = true
        )
        if (onDelete != null)
          TextButton(onClick = { askDelete = true }) {
            Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(LanguagesText.delete(), color = MaterialTheme.colorScheme.error)
          }
      }
    },
    confirmButton = {
      TextButton(enabled = word.isNotBlank() && translation.isNotBlank(), onClick = confirm) {
        Text(LanguagesText.save())
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(LanguagesText.cancel()) } }
  )
  if (askDelete && onDelete != null)
    ConfirmDialog(
      LanguagesText.deleteEntryTitle(),
      LanguagesText.deleteEntryText(),
      confirm = LanguagesText.delete(),
      onConfirm = onDelete,
      onDismiss = { askDelete = false }
    )
}
