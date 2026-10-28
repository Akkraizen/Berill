package dev.akkraizen.berill.plugins.languages.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.languages.LanguagesPlugin
import dev.akkraizen.berill.plugins.languages.data.LESSON
import dev.akkraizen.berill.plugins.languages.data.Language
import dev.akkraizen.berill.plugins.languages.data.LanguageSelection
import dev.akkraizen.berill.plugins.languages.data.Lesson
import dev.akkraizen.berill.plugins.languages.data.RULE
import dev.akkraizen.berill.plugins.languages.data.WORDS_SECTION
import dev.akkraizen.berill.plugins.languages.data.Word
import dev.akkraizen.berill.plugins.languages.data.byCode
import dev.akkraizen.berill.plugins.languages.data.lessons
import dev.akkraizen.berill.plugins.languages.data.naturalOrder
import dev.akkraizen.berill.plugins.languages.data.rememberLanguages
import dev.akkraizen.berill.plugins.languages.data.saveLanguages
import dev.akkraizen.berill.plugins.languages.data.selected
import dev.akkraizen.berill.plugins.languages.data.wordOfDay
import dev.akkraizen.berill.plugins.languages.data.words
import dev.akkraizen.berill.plugins.languages.i18n.LanguagesText
import kotlin.random.Random

internal val LESSON_ICON: ImageVector
  get() = Icons.Outlined.Class
internal val RULE_ICON: ImageVector
  get() = Icons.Outlined.Straighten
internal val DICT_ICON: ImageVector
  get() = Icons.Outlined.MenuBook

@Composable
internal fun LanguagesHome(ctx: PluginContext) {
  val wordCollection = remember(ctx) { words(ctx) }
  val lessonCollection = remember(ctx) { lessons(ctx) }
  val allWords by remember(wordCollection) { wordCollection.observe() }.collectAsState(emptyList())
  val allLessons by
  remember(lessonCollection) { lessonCollection.observe() }.collectAsState(emptyList())
  val languages = rememberLanguages(ctx)
  var addingLanguage by remember { mutableStateOf(false) }

  fun addLanguage(added: Language) {
    saveLanguages(ctx, languages + added)
    LanguageSelection.code = added.code
    addingLanguage = false
  }

  val addDialog: @Composable () -> Unit = {
    if (addingLanguage)
      LanguageDialog(
        initial = null,
        takenCodes = languages.map { it.code }.toSet(),
        onDismiss = { addingLanguage = false },
        onSave = ::addLanguage
      )
  }

  if (languages.isEmpty()) {
    PluginList {
      item {
        SectionCard(LanguagesText.title(), Icons.Outlined.Translate) {
          EmptyHint(LanguagesText.emptyLanguages())
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { addingLanguage = true }) {
              Ic(BerillIcons.Add, size = 18.dp)
              Spacer(Modifier.width(8.dp))
              Text(LanguagesText.addLanguage())
            }
            OutlinedButton(onClick = { ctx.nav.openSettings() }) { Text(LanguagesText.settings()) }
          }
        }
        addDialog()
      }
    }
    return
  }

  val lang = languages.selected() ?: return
  val codes = languages.map { it.code }
  val entries = allWords.map { it.value }.filter { it.language == lang }
  val langLessons = allLessons
    .filter { it.value.language == lang && it.value.kind == LESSON }
    .sortedWith(compareBy(naturalOrder) { it.value.title })
  val rules = allLessons
    .filter { it.value.language == lang && it.value.kind == RULE }
    .sortedWith(compareBy(naturalOrder) { it.value.title })
  val current = languages.byCode(lang)

  fun newLesson(kind: String) {
    val id = ctx.data.newId()
    val number = langLessons.size + 1
    val title = if (kind == LESSON)
      "${current.lessonWord} ${number.toString().padStart(2, '0')}"
    else
      LanguagesText.newRule()
    lessonCollection.save(id, Lesson(lang, kind, title, today().toString()))
    ctx.nav.open(LanguagesPlugin.LESSON_ROUTE + id)
  }

  PluginList {
    item {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        ChoiceChips(
          codes,
          lang,
          { languages.byCode(it).let { l -> "${l.flag} ${l.name}" } },
          { LanguageSelection.code = it },
          Modifier.weight(1f, fill = false)
        )
        BerillChip(
          onClick = { addingLanguage = true },
          label = { Text(LanguagesText.language(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          leadingIcon = { Ic(BerillIcons.Add, size = 18.dp) }
        )

        IconButton(onClick = { ctx.nav.openSettings() }) {
          Ic(Icons.Outlined.Settings, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      addDialog()
    }
    item {
      TwoPane(
        left = {
          Trainer(entries, current, allLessons) { id ->
            ctx.nav.open(LanguagesPlugin.LESSON_ROUTE + id)
          }
        },
        right = {
          val wod = wordOfDay(entries, today().toString(), lang)
          SectionCard(LanguagesText.wordOfDay(), Icons.Outlined.Lightbulb) {
            if (wod == null)
              EmptyHint(LanguagesText.dictionaryEmpty())
            else {
              Text(wod.word, style = MaterialTheme.typography.headlineSmall)
              Text(
                listOfNotNull(wod.pron, wod.translation).joinToString(" — "),
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          SectionCard(
            LanguagesText.lessons(),
            LESSON_ICON,
            trailing = {
              FilledTonalButton(
                onClick = {
                  LanguageSelection.code = lang
                  ctx.nav.openPage(LanguagesPlugin.PAGE_DICTIONARY)
                }
              ) {
                Ic(DICT_ICON, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(LanguagesText.dictionary())
              }
            }
          ) {
            val latest = langLessons.lastOrNull()
            if (latest != null) {
              val date = latest.value.date.toDateOrNull()?.let { " · ${it.dayMonth()}" } ?: ""
              Text(
                LanguagesText.latestLesson(latest.value.title)() + date,
                fontWeight = FontWeight.SemiBold
              )
            }
            if (langLessons.isEmpty()) EmptyHint(LanguagesText.noLessons())
            langLessons.forEach { item ->
              Text(
                item.value.title,
                modifier = Modifier.fillMaxWidth()
                  .interactive(bleed = 6.dp) {
                    ctx.nav.open(LanguagesPlugin.LESSON_ROUTE + item.id)
                  }
                  .padding(vertical = 4.dp)
              )
            }
            if (rules.isNotEmpty())
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                rules.forEach { item ->
                  BerillChip(
                    onClick = { ctx.nav.open(LanguagesPlugin.LESSON_ROUTE + item.id) },
                    label = { Text(item.value.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Ic(RULE_ICON, size = 18.dp) }
                  )
                }
              }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(onClick = { newLesson(LESSON) }) {
                Ic(BerillIcons.Add, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(LanguagesText.lesson())
              }
              TextButton(onClick = { newLesson(RULE) }) {
                Ic(BerillIcons.Add, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(LanguagesText.rule())
              }
            }
          }
        }
      )
    }
    item { LanguageStatistics(langLessons.map { it.value }) }
  }
}

@Composable
private fun Trainer(
  entries: List<Word>,
  lang: Language,
  lessons: List<Item<Lesson>>,
  openLesson: (String) -> Unit
) {
  var reverse by rememberSaveable { mutableStateOf(false) }
  var onlyWords by rememberSaveable { mutableStateOf(false) }
  var ok by remember { mutableIntStateOf(0) }
  var bad by remember { mutableIntStateOf(0) }
  val pool = if (onlyWords) entries.filter { it.section == WORDS_SECTION } else entries
  var index by
  remember(pool.size) {
    mutableIntStateOf(if (pool.isEmpty()) 0 else Random.nextInt(pool.size))
  }
  var revealed by remember(index) { mutableStateOf(false) }

  fun next(known: Boolean) {
    if (known) ok++ else bad++
    if (pool.size > 1) {
      var n: Int
      do {
        n = Random.nextInt(pool.size)
      } while (n == index)
      index = n
    } else
      revealed = false
  }

  SectionCard(
    LanguagesText.trainer(),
    Icons.Outlined.Style,
    trailing = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Ic(BerillIcons.Check, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
        Text("$ok", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(6.dp))
        Ic(BerillIcons.Close, size = 16.dp, tint = MaterialTheme.colorScheme.error)
        Text("$bad", style = MaterialTheme.typography.labelLarge)
      }
    }
  ) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { reverse = !reverse }) {
        Text(if (reverse) "RU" else lang.flag)
        Ic(BerillIcons.Forward, size = 16.dp, modifier = Modifier.padding(horizontal = 6.dp))
        Text(if (reverse) lang.flag else "RU")
      }
      OutlinedButton(onClick = { onlyWords = !onlyWords }) {
        Text(if (onlyWords) LanguagesText.onlyWords() else LanguagesText.wordsAndPhrases())
      }
    }
    val card = pool.getOrNull(index.coerceIn(0, (pool.size - 1).coerceAtLeast(0)))
    if (card == null) {
      EmptyHint(LanguagesText.dictionaryEmpty())
      return@SectionCard
    }
    Surface(
      onClick = { revealed = !revealed },
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
    ) {
      Column(
        Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          if (reverse) card.translation else card.word,
          style = MaterialTheme.typography.headlineMedium,
          textAlign = TextAlign.Center
        )
        if (!reverse && card.pron != null)
          Text(card.pron, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        if (!revealed) {
          Text(
            LanguagesText.tapToReveal(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
          )
        } else {
          Text(
            if (reverse) card.word else card.translation,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
          )
          if (reverse && card.pron != null) Text(card.pron)
          card.lesson?.let { name ->
            val lesson = lessons.firstOrNull {
              it.value.title == name && it.value.language == card.language
            }
            Row(
              modifier = if (lesson != null)
                Modifier.interactive(bleed = 6.dp) { openLesson(lesson.id) }
              else
                Modifier,
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Ic(LESSON_ICON, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
              Text(name, color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { next(false) }, modifier = Modifier.weight(1f)) {
        Ic(BerillIcons.Close, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(LanguagesText.dontKnow())
      }
      Button(onClick = { next(true) }, modifier = Modifier.weight(1f)) {
        Ic(BerillIcons.Check, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(LanguagesText.know())
      }
    }
  }
}
