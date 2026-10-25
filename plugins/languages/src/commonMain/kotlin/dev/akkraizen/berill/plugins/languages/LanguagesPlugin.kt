package dev.akkraizen.berill.plugins.languages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPage
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import kotlinx.serialization.Serializable

object LanguagesPlugin : BerillPlugin {
  const val WORDS = "lang_words"
  const val LESSONS = "lang_lessons"

  override val id = "languages"
  override val title
    get() = LanguagesText.title()

  override val icon = Icons.Outlined.Translate
  override val order = 55
  override val description
    get() = LanguagesText.description()

  override val installedByDefault = true
  override val collections = listOf(WORDS, LESSONS)
  override val hasSettings = true

  @Composable
  override fun Settings(ctx: PluginContext) = LanguagesSettings(ctx)

  internal const val LESSON_ROUTE = "lesson:"

  const val PAGE_OVERVIEW = "overview"
  const val PAGE_DICTIONARY = "dictionary"

  override val pages = listOf(
    PluginPage(PAGE_OVERVIEW, LanguagesText.overview, Icons.Outlined.Translate) { ctx ->
      OverviewPage(ctx)
    },
    PluginPage(PAGE_DICTIONARY, LanguagesText.dictionary, Icons.Outlined.MenuBook) { ctx ->
      DictionaryScreen(ctx)
    }
  )
}

@Composable
private fun OverviewPage(ctx: PluginContext) {
  val route by ctx.nav.route.collectAsState()
  AnimatedRoute(route) { r ->
    if (r != null && r.startsWith(LanguagesPlugin.LESSON_ROUTE))
      LessonScreen(ctx, r.removePrefix(LanguagesPlugin.LESSON_ROUTE))
    else
      LanguagesHome(ctx)
  }
}

internal object LanguageSelection {
  var code: String by mutableStateOf("")
}

internal fun List<Language>.selected(): String? = LanguageSelection.code.takeIf { c -> any { it.code == c } } ?: firstOrNull()?.code

const val WORDS_SECTION = "Слова"
const val PHRASES_SECTION = "Фразы"

@Serializable
data class Word(
  val language: String = "de",
  val section: String = WORDS_SECTION,
  val word: String = "",
  val pron: String? = null,
  val translation: String = "",
  val pos: String? = null,
  val lesson: String? = null
)

const val LESSON = "lesson"
const val RULE = "rule"

@Serializable
data class Lesson(
  val language: String = "de",
  val kind: String = LESSON,
  val title: String = "",
  val date: String? = null,
  val body: String? = null
)

internal fun words(ctx: PluginContext) = ctx.data.collection(LanguagesPlugin.WORDS, Word.serializer())

internal fun lessons(ctx: PluginContext) = ctx.data.collection(LanguagesPlugin.LESSONS, Lesson.serializer())

val naturalOrder: Comparator<String> = Comparator { a, b ->
  val re = Regex("""\d+|\D+""")
  val pa = re.findAll(a.lowercase()).map { it.value }.toList()
  val pb = re.findAll(b.lowercase()).map { it.value }.toList()
  for (i in 0 until minOf(pa.size, pb.size)) {
    val x = pa[i]
    val y = pb[i]
    val c = if (x[0].isDigit() && y[0].isDigit())
      compareValues(x.toBigIntegerLike(), y.toBigIntegerLike())
    else
      x.compareTo(y)
    if (c != 0) return@Comparator c
  }
  pa.size - pb.size
}

private fun String.toBigIntegerLike(): Long = trimStart('0').take(18).ifEmpty { "0" }.toLong()

fun wordOfDay(entries: List<Word>, dayIso: String, lang: String): Word? {
  if (entries.isEmpty()) return null
  var acc = 7L
  for (ch in dayIso + lang) acc = (acc * 31 + ch.code) and 0xFFFFFFFFL
  return entries[(acc % entries.size).toInt()]
}
