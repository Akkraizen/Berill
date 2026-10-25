package com.example.berill.counters

import dev.akkraizen.berill.sdk.ButtonStyle
import dev.akkraizen.berill.sdk.ExternalPlugin
import dev.akkraizen.berill.sdk.Stat
import dev.akkraizen.berill.sdk.TextStyle
import dev.akkraizen.berill.sdk.Tone
import dev.akkraizen.berill.sdk.UiBuilder
import dev.akkraizen.berill.sdk.collection
import dev.akkraizen.berill.sdk.i18n.TranslationCatalog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import berill.generated.localization.CountersCatalogData

@Serializable
data class Counter(val title: String = "", val value: Int = 0, val step: Int = 1)

private const val COLLECTION = "counters_items"
private const val SHOW_TOTAL = "showTotal"

class CountersPlugin : ExternalPlugin() {
  override val translations = TranslationCatalog.fromJson(CountersCatalogData.files)

  private val counters by lazy { host.data.collection<Counter>(COLLECTION) }

  private var draft = ""

  override fun onStart() {
    host.log("counters started")
  }

  override fun UiBuilder.page(pageId: String, route: String?) {
    when {
      pageId == "about" -> about()
      route != null -> details(route)
      else -> list()
    }
  }

  private fun UiBuilder.list() {
    val all = counters.all().sortedBy { it.second.title.lowercase() }
    card(tr("countersPlugin.list"), icon = "add") {
      row {
        weight {
          textField(draft, tr("countersPlugin.list.2"), key = "new", onSubmit = { add() }) {
            draft = it
          }
        }
        button(tr("countersPlugin.list.3"), enabled = draft.isNotBlank()) { add() }
      }
    }
    if (all.isEmpty()) {
      empty(tr("countersPlugin.list.4"))
      return
    }
    all.forEach { (id, c) ->
      card(c.title, icon = "timer") {
        row {
          weight { text(c.value.toString(), TextStyle.Headline) }
          button("−${c.step}", ButtonStyle.Tonal) {
            change(id) { it.copy(value = it.value - it.step) }
          }
          button("+${c.step}") { change(id) { it.copy(value = it.value + it.step) } }
          iconButton("edit", tr("countersPlugin.list.5")) { host.nav.open(id) }
        }
      }
    }
  }

  private fun UiBuilder.details(id: String) {
    val c = counters.get(id)
    if (c == null) {
      empty(tr("countersPlugin.details"))
      return
    }
    header(c.title, "timer")
    card {
      textField(c.title, tr("countersPlugin.details.2"), key = "title-$id") { v ->
        if (v.isNotBlank()) change(id) { it.copy(title = v.trim()) }
      }
      text(tr("countersPlugin.details.3"), TextStyle.Label, Tone.Muted)
      chips(listOf(1, 5, 10, 50).map { "$it" to "$it" }, c.step.toString()) { s ->
        change(id) { it.copy(step = s.toInt()) }
      }
      row {
        button(tr("countersPlugin.details.4"), ButtonStyle.Outlined) {
          change(id) { it.copy(value = 0) }
          host.toast(tr("countersPlugin.details.5"))
        }
        button(tr("countersPlugin.details.6"), ButtonStyle.Text, icon = "delete") {
          counters.delete(id)
          host.nav.back()
        }
      }
    }
  }

  private fun UiBuilder.about() {
    card(tr("countersPlugin.about"), icon = "info") {
      markdown(tr("countersPlugin.about.2"))
    }
  }

  override fun UiBuilder.homeCard(cardId: String) {
    val all = counters.all().map { it.second }
    val showTotal = host.settings.get(SHOW_TOTAL)?.jsonPrimitive?.booleanOrNull ?: true
    stats(
      buildList {
        add(Stat(tr("countersPlugin.homeCard"), all.size.toString(), icon = "timer"))
        if (showTotal)
          add(Stat(tr("countersPlugin.homeCard.2"), all.sumOf { it.value }.toString(), tone = Tone.Good))
      }
    )
  }

  override fun UiBuilder.settings() {
    val showTotal = host.settings.get(SHOW_TOTAL)?.jsonPrimitive?.booleanOrNull ?: true
    switch(showTotal, tr("countersPlugin.settings")) { on ->
      host.settings.set(SHOW_TOTAL, JsonPrimitive(on))
    }
  }

  private fun add() {
    val title = draft.trim()
    if (title.isEmpty()) return
    counters.save(host.data.newId(), Counter(title))
    draft = ""
  }

  private fun change(id: String, update: (Counter) -> Counter) {
    val current = counters.get(id) ?: return
    counters.save(id, update(current))
  }
}
