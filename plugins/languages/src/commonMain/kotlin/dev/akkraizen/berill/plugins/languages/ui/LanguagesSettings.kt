package dev.akkraizen.berill.plugins.languages.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.plugins.languages.data.Language
import dev.akkraizen.berill.plugins.languages.data.deleteLanguageData
import dev.akkraizen.berill.plugins.languages.data.lessons
import dev.akkraizen.berill.plugins.languages.data.normalizeLanguageCode
import dev.akkraizen.berill.plugins.languages.data.rememberLanguages
import dev.akkraizen.berill.plugins.languages.data.saveLanguages
import dev.akkraizen.berill.plugins.languages.data.words
import dev.akkraizen.berill.plugins.languages.i18n.LanguagesText

@Composable
internal fun LanguagesSettings(ctx: PluginContext) {
  val languages = rememberLanguages(ctx)
  val words = remember(ctx) { words(ctx) }
  val lessons = remember(ctx) { lessons(ctx) }
  val allWords by remember(words) { words.observe() }.collectAsState(emptyList())
  val allLessons by remember(lessons) { lessons.observe() }.collectAsState(emptyList())
  var editing by remember { mutableStateOf<Language?>(null) }
  var adding by remember { mutableStateOf(false) }
  var removing by remember { mutableStateOf<Language?>(null) }

  val hidden = (allWords.map { it.value.language } + allLessons.map { it.value.language })
    .filter { code -> languages.none { it.code == code } }
    .groupingBy { it }
    .eachCount()
  var restoring by remember { mutableStateOf<String?>(null) }
  var purging by remember { mutableStateOf<String?>(null) }

  SectionCard(
    LanguagesText.studiedLanguages(),
    Icons.Outlined.Translate,
    trailing = {
      FilledTonalButton(onClick = { adding = true }) {
        Ic(BerillIcons.Add, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(LanguagesText.language())
      }
    }
  ) {
    if (languages.isEmpty()) EmptyHint(LanguagesText.listEmpty())
    languages.forEachIndexed { index, l ->
      val wordCount = allWords.count { it.value.language == l.code }
      val lessonCount = allLessons.count { it.value.language == l.code }
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        LanguageBadge(l)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
          Text("${l.name} · ${l.nativeName}", style = MaterialTheme.typography.titleSmall)
          Text(
            LanguagesText.languageSummary(l.code, l.lessonWord, wordCount, lessonCount)(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        IconButton(onClick = { saveLanguages(ctx, languages.swap(index, index - 1)) }, enabled = index > 0) {
          Ic(Icons.Outlined.KeyboardArrowUp)
        }
        IconButton(
          onClick = { saveLanguages(ctx, languages.swap(index, index + 1)) },
          enabled = index < languages.lastIndex
        ) {
          Ic(Icons.Outlined.KeyboardArrowDown)
        }
        IconButton(onClick = { editing = l }) { Ic(BerillIcons.Edit) }
        IconButton(onClick = { removing = l }) {
          Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.error)
        }
      }
    }
    Text(
      LanguagesText.codeHint(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }

  if (hidden.isNotEmpty())
    SectionCard(LanguagesText.hiddenRecords(), Icons.Outlined.VisibilityOff) {
      hidden.forEach { (code, count) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(LanguagesText.hiddenCount(code, count)(), modifier = Modifier.weight(1f))
          TextButton(onClick = { restoring = code }) { Text(LanguagesText.restoreLanguage()) }
          TextButton(onClick = { purging = code }) {
            Text(LanguagesText.delete(), color = MaterialTheme.colorScheme.error)
          }
        }
      }
    }

  if (adding)
    LanguageDialog(
      initial = null,
      takenCodes = languages.map { it.code }.toSet(),
      onDismiss = { adding = false },
      onSave = {
        saveLanguages(ctx, languages + it)
        adding = false
      }
    )
  restoring?.let { code ->
    LanguageDialog(
      initial = Language(code),
      isNew = true,
      takenCodes = languages.map { it.code }.toSet(),
      onDismiss = { restoring = null },
      onSave = {
        saveLanguages(ctx, languages + it)
        restoring = null
      }
    )
  }
  editing?.let { l ->
    LanguageDialog(
      initial = l,
      takenCodes = emptySet(),
      onDismiss = { editing = null },
      onSave = { updated ->
        saveLanguages(ctx, languages.map { if (it.code == l.code) updated else it })
        editing = null
      }
    )
  }
  removing?.let { l ->
    RemoveLanguageDialog(
      language = l,
      records = allWords.count { it.value.language == l.code } +
        allLessons.count { it.value.language == l.code },
      onKeepData = { saveLanguages(ctx, languages.filter { it.code != l.code }) },
      onDeleteData = {
        deleteLanguageData(ctx, l.code)
        saveLanguages(ctx, languages.filter { it.code != l.code })
      },
      onDismiss = { removing = null }
    )
  }
  purging?.let { code ->
    ConfirmDialog(
      title = LanguagesText.purgeTitle(code)(),
      text = LanguagesText.purgeText(),
      onConfirm = { deleteLanguageData(ctx, code) },
      onDismiss = { purging = null }
    )
  }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> =
  if (a !in indices || b !in indices)
    this
  else
    toMutableList().also {
      it[a] = this[b]
      it[b] = this[a]
    }

@Composable
internal fun RemoveLanguageDialog(
  language: Language,
  records: Int,
  onKeepData: () -> Unit,
  onDeleteData: () -> Unit,
  onDismiss: () -> Unit
) {
  val confirm: () -> Unit = {
    onKeepData()
    onDismiss()
  }
  DialogKeys(onConfirm = confirm, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(LanguagesText.removeTitle(language.nativeName)()) },
    text = {
      Text(
        if (records == 0)
          LanguagesText.removeNoRecords()
        else
          LanguagesText.removeWithRecords(records, language.code)()
      )
    },
    confirmButton = {
      Row {
        if (records > 0)
          TextButton(
            onClick = {
              onDeleteData()
              onDismiss()
            }
          ) {
            Text(LanguagesText.deleteWithRecords(), color = MaterialTheme.colorScheme.error)
          }
        TextButton(onClick = confirm) { Text(LanguagesText.remove()) }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(LanguagesText.cancel()) } }
  )
}

@Composable
internal fun LanguageDialog(
  initial: Language?,
  takenCodes: Set<String>,
  isNew: Boolean = initial == null,
  onDismiss: () -> Unit,
  onSave: (Language) -> Unit
) {
  var code by remember { mutableStateOf(initial?.code ?: "") }
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var nativeName by remember { mutableStateOf(initial?.nativeName ?: "") }
  var flag by remember { mutableStateOf(initial?.flag ?: "") }
  var lessonWord by remember { mutableStateOf(initial?.lessonWord ?: "") }

  val normalized = normalizeLanguageCode(code)
  val codeError = when {
    !isNew -> null
    normalized.isEmpty() -> LanguagesText.codeRequired()
    normalized in takenCodes -> LanguagesText.codeTaken()
    else -> null
  }
  val valid = codeError == null && name.isNotBlank()
  val confirm: () -> Unit = {
    val key = initial?.code ?: normalized
    onSave(
      Language(
        code = key,
        flag = flag.trim().ifEmpty { key.uppercase() },
        name = name.trim(),
        nativeName = nativeName.trim().ifEmpty { name.trim() },
        lessonWord = lessonWord.trim().ifEmpty { "Урок" }
      )
    )
  }

  DialogKeys(onConfirm = confirm, confirmEnabled = valid, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        if (isNew)
          LanguagesText.newLanguage()
        else
          LanguagesText.languageTitle(initial?.nativeName ?: "")()
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(
          code,
          { code = it },
          label = { Text(LanguagesText.codeLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true,
          enabled = initial == null,
          isError = code.isNotEmpty() && codeError != null,
          supportingText = if (code.isNotEmpty() && codeError != null) {
            { Text(codeError) }
          } else
            null
        )
        BerillTextField(name, { name = it }, label = { Text(LanguagesText.selfNameLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true)
        BerillTextField(
          nativeName,
          { nativeName = it },
          label = { Text(LanguagesText.nativeNameLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true
        )
        BerillTextField(
          flag,
          { flag = it.take(4) },
          label = { Text(LanguagesText.flagLabel(normalized.uppercase().ifEmpty { "FR" })(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true
        )
        BerillTextField(
          lessonWord,
          { lessonWord = it },
          label = { Text(LanguagesText.lessonWordLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true
        )
      }
    },
    confirmButton = {
      TextButton(enabled = valid, onClick = confirm) {
        Text(if (isNew) LanguagesText.add() else LanguagesText.save())
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(LanguagesText.cancel()) } }
  )
}
