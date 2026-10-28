package dev.akkraizen.berill.plugins.languages.widgets

import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetButton
import dev.akkraizen.berill.api.widgets.WidgetChoice
import dev.akkraizen.berill.api.widgets.WidgetChoices
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetSize
import dev.akkraizen.berill.plugins.languages.LanguagesPlugin
import dev.akkraizen.berill.plugins.languages.data.LANGUAGES_KEY
import dev.akkraizen.berill.plugins.languages.data.Word
import dev.akkraizen.berill.plugins.languages.data.byCode
import dev.akkraizen.berill.plugins.languages.data.decodeLanguages
import dev.akkraizen.berill.plugins.languages.data.words
import dev.akkraizen.berill.plugins.languages.i18n.LanguagesText

internal fun languagesWidgets(ctx: PluginContext): List<WidgetProvider> = listOf(FlashcardWidget(ctx))

internal fun nextCardIndex(current: Int, size: Int, seed: Long): Int {
  if (size <= 1) return 0
  val step = 1 + ((seed xor (seed ushr 17)) and 0x7FFFFFFF).toInt() % (size - 1)
  return (current + step) % size
}

internal fun dayCardIndex(size: Int, dayIso: String): Int {
  if (size == 0) return 0
  var acc = 7L
  for (ch in dayIso) acc = (acc * 31 + ch.code) and 0xFFFFFFFFL
  return (acc % size).toInt()
}

private class FlashcardWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "card"
  override val title: Tr = LanguagesText.widgetCardTitle
  override val description: Tr = LanguagesText.widgetCardDescription
  override val size = WidgetSize.Medium

  private fun languages() = decodeLanguages(ctx.settings.get(LANGUAGES_KEY))

  override val choices = WidgetChoices(LanguagesText.widgetCardChoose, required = false) {
    languages().map { WidgetChoice(it.code, it.nativeName.ifBlank { it.name }, it.flag) }
  }

  private fun deck(choice: String?): List<Item<Word>> =
    words(ctx).all()
      .filter { it.value.word.isNotBlank() && it.value.translation.isNotBlank() }
      .filter { choice == null || it.value.language == choice }
      .sortedBy { it.id }

  private fun indexOf(request: WidgetRequest, deck: List<Item<Word>>): Int {
    val id = request.state[WORD]
    val found = id?.let { wanted -> deck.indexOfFirst { it.id == wanted } }?.takeIf { it >= 0 }
    return found ?: dayCardIndex(deck.size, today().toString() + (request.choice ?: ""))
  }

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val deck = deck(request.choice)
    val languages = languages()
    if (deck.isEmpty())
      return WidgetContent(
        title = title(),
        icon = WidgetIcon.Translate,
        open = WidgetAction.Open(page = LanguagesPlugin.PAGE_DICTIONARY),
        empty = LanguagesText.widgetNoWords()
      )
    val index = indexOf(request, deck)
    val word = deck[index].value
    val revealed = request.state[REVEALED] == "1"
    val language = languages.byCode(word.language)
    return WidgetContent(
      title = title(),
      subtitle = listOf(language.nativeName.ifBlank { language.name }, "${index + 1}/${deck.size}").joinToString(" · "),
      icon = WidgetIcon.Translate,
      open = WidgetAction.Open(page = LanguagesPlugin.PAGE_DICTIONARY),
      blocks = listOf(
        WidgetBlock.Card(
          front = word.word,
          back = if (revealed) word.translation else null,
          hint = listOfNotNull(word.pron?.ifBlank { null }?.let { "[$it]" }, word.pos?.ifBlank { null }).joinToString(" · ").ifBlank { null }
        ),
        WidgetBlock.Buttons(
          listOfNotNull(
            if (revealed) null else WidgetButton(LanguagesText.widgetShow(), WidgetAction.Run(REVEAL), WidgetIcon.Open, primary = true),
            WidgetButton(LanguagesText.widgetNext(), WidgetAction.Run(NEXT), WidgetIcon.Next, primary = revealed)
          )
        )
      )
    )
  }

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect {
    val deck = deck(request.choice)
    if (deck.isEmpty()) return WidgetEffect.Refresh(state = emptyMap())
    val index = indexOf(request, deck)
    return when (action.id) {
      REVEAL -> WidgetEffect.Refresh(state = mapOf(WORD to deck[index].id, REVEALED to "1"))
      NEXT -> WidgetEffect.Refresh(state = mapOf(WORD to deck[nextCardIndex(index, deck.size, request.nowMillis)].id))
      else -> WidgetEffect.Refresh()
    }
  }

  private companion object {
    const val WORD = "word"
    const val REVEALED = "revealed"
    const val REVEAL = "reveal"
    const val NEXT = "next"
  }
}
