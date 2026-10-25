package dev.akkraizen.berill.plugins.languages

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class Language(
  val code: String,
  val flag: String = code.uppercase(),
  val name: String = code,
  val nativeName: String = name,
  val lessonWord: String = "Урок"
)

val BUILT_IN_LANGUAGES = listOf(
  Language("en", "EN", "English", "Английский", "Lesson")
)

internal const val LANGUAGES_KEY = "languages"

private val languagesJson = Json {
  ignoreUnknownKeys = true
  coerceInputValues = true
  encodeDefaults = true
}
private val languagesSerializer = ListSerializer(Language.serializer())

internal fun decodeLanguages(value: JsonElement?): List<Language> {
  if (value == null) return BUILT_IN_LANGUAGES
  val list =
    runCatching { languagesJson.decodeFromJsonElement(languagesSerializer, value) }.getOrNull()
      ?: return BUILT_IN_LANGUAGES
  return list.filter { it.code.isNotBlank() }.distinctBy { it.code }
}

internal fun deleteLanguageData(ctx: PluginContext, code: String) {
  val w = words(ctx)
  w.all().filter { it.value.language == code }.forEach { w.delete(it.id) }
  val l = lessons(ctx)
  l.all().filter { it.value.language == code }.forEach { l.delete(it.id) }
}

internal fun saveLanguages(ctx: PluginContext, languages: List<Language>) =
  ctx.settings.set(LANGUAGES_KEY, languagesJson.encodeToJsonElement(languagesSerializer, languages))

@Composable
internal fun rememberLanguages(ctx: PluginContext): List<Language> {
  val value by
  remember(ctx) { ctx.settings.observe(LANGUAGES_KEY) }
    .collectAsState(ctx.settings.get(LANGUAGES_KEY))
  return remember(value) { decodeLanguages(value) }
}

internal fun List<Language>.byCode(code: String): Language =
  firstOrNull { it.code == code } ?: Language(code)

internal fun normalizeLanguageCode(text: String): String =
  text.trim().lowercase().filter { it in 'a'..'z' || it.isDigit() || it == '-' }.take(12)

@Composable
internal fun LanguageBadge(language: Language) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.small
  ) {
    Text(
      language.flag,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      modifier = Modifier.widthIn(min = 40.dp).padding(horizontal = 8.dp, vertical = 6.dp)
    )
  }
}
