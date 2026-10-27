package dev.akkraizen.berill.plugins.languages.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.plugins.languages.LanguagesPlugin
import kotlinx.serialization.Serializable

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
