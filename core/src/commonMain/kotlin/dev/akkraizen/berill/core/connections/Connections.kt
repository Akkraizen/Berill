package dev.akkraizen.berill.core.connections

import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.widgets.*
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.notes.displayTitle
import dev.akkraizen.berill.core.widgets.WidgetInstance
import dev.akkraizen.berill.protocol.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class Connections(private val core: BerillCore) {
  val errors = FeatureErrors(onProgress = { operation, progress -> core.notifications.operation(operation, progress) }, onReport = { core.notifications.report(it) })
  val searchPartial = MutableStateFlow(false)
  val dayPartial = MutableStateFlow(false)
  val shared = MutableStateFlow<SharedText?>(null)
  val searchOpen = MutableStateFlow(false)
  val dayOpen = MutableStateFlow(false)
  val trashOpen = MutableStateFlow(false)

  suspend fun search(query: String): List<SearchResult> {
    val results = mutableListOf<SearchResult>()
    var partial = false
    suspend fun <T> read(source: String, block: suspend () -> T): T? = tolerateSource(source, { partial = true }, block)
    val q = query.trim()
    fun add(id: String, title: String, subtitle: String, run: () -> Unit) {
      if (q.isBlank() || q.split(' ').all { it.isBlank() || (title + " " + subtitle).contains(it, ignoreCase = true) })
        results += SearchResult(id, title, subtitle, run)
    }
    add("note:new", ConnectionsText.newNote(), ConnectionsText.command()) { core.nav.openNote(null) }
    add("day", ConnectionsText.day(), ConnectionsText.command()) { dayOpen.value = true }
    for (plugin in core.plugins.enabled.value) {
      val ctx = read(plugin.id) { core.plugins.context(plugin) } ?: continue
      for (action in read(plugin.id) { plugin.quickActions(ctx) }.orEmpty()) read(plugin.id) { add("${plugin.id}:${action.id}", action.label(), plugin.title, action.run) }
      for (source in read(plugin.id) { plugin.provides(ctx) }.orEmpty().filterIsInstance<SearchSource>()) {
        results += read(plugin.id) { source.search(q) }.orEmpty()
      }
    }
    for (key in listOf("habits.today", "finance.quick", "nutrition.water")) {
      if (read(key) { core.widgets.available() }.orEmpty().none { it.key == key }) continue
      val instance = WidgetInstance(key)
      val content = read(key) { core.widgets.content(instance) } ?: continue
      val buttons = content.buttons + content.blocks.flatMap {
        when (it) {
          is WidgetBlock.Checks -> it.items.map { check -> WidgetButton(ConnectionsText.markHabit() + ": " + check.label, check.action) }
          is WidgetBlock.Buttons -> it.items
          else -> emptyList()
        }
      }
      for ((i, button) in buttons.withIndex()) add("$key:$i", button.label, content.title) {
        core.scope.launch { errors.runSuspending(FailureOperation.COMMAND) { core.widgets.perform(instance, button.action) } }
      }
    }
    if (q.isNotBlank()) read("notes") { noteMatches(q) }.orEmpty()
      .forEach { (id, title) -> results += SearchResult("note:$id", title, ConnectionsText.notes()) { core.nav.openNote(id) } }
    searchPartial.value = partial
    return results.distinctBy { it.id }.take(100)
  }

  /** Notes matching [query]: ranked full-text hits, or a scan when the store has no index. */
  private fun noteMatches(query: String): List<Pair<String, String>> {
    val indexed = core.store.search(query, setOf(Collections.NOTES), 50)
    if (indexed != null) return indexed.mapNotNull { record -> core.notes.get(record.id)?.let { record.id to it.displayTitle() } }
    return core.notes.all().filter { (it.value.title + " " + it.value.body).contains(query, ignoreCase = true) }
      .take(50).map { it.id to it.value.displayTitle() }
  }

  suspend fun day(date: LocalDate): List<DayEntry> {
    var partial = false
    val entries = mutableListOf<DayEntry>()
    for (plugin in core.plugins.enabled.value) {
      val sources = tolerateSource(plugin.id, { partial = true }) { plugin.provides(core.plugins.context(plugin)) }.orEmpty()
      for (source in sources.filterIsInstance<DaySummarySource>())
        entries += tolerateSource(plugin.id, { partial = true }) { source.entries(date) }.orEmpty()
    }
    dayPartial.value = partial
    return entries
  }
}

internal suspend fun <T> tolerateSource(source: String, onFailure: () -> Unit, block: suspend () -> T): T? = try {
  block()
} catch (e: CancellationException) { throw e } catch (e: Exception) {
  dev.akkraizen.berill.core.log.AppLog.error("feature-source", source, e)
  onFailure()
  null
}
