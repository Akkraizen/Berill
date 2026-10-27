package dev.akkraizen.berill.plugins.languages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPage
import dev.akkraizen.berill.plugins.languages.i18n.LanguagesText
import dev.akkraizen.berill.plugins.languages.ui.DictionaryScreen
import dev.akkraizen.berill.plugins.languages.ui.LanguagesSettings
import dev.akkraizen.berill.plugins.languages.ui.OverviewPage

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
