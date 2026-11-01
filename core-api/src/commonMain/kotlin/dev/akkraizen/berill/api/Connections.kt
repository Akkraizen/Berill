package dev.akkraizen.berill.api

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*

interface SearchSource : Capability {
  suspend fun search(query: String): List<SearchResult>
}

data class SearchResult(val id: String, val title: String, val subtitle: String, val open: () -> Unit)

interface DaySummarySource : Capability {
  suspend fun entries(date: LocalDate): List<DayEntry>
}

data class DayEntry(val id: String, val title: String, val detail: String, val open: () -> Unit)

// Opt-in providers use only their own scoped DataAccess; no plugin dependencies.
class CollectionSearchSource(
  private val ctx: PluginContext,
  private val collection: String,
  private val label: () -> String,
  private val route: (String) -> String? = { null }
) : SearchSource {
  override suspend fun search(query: String): List<SearchResult> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return emptyList()
    return ctx.data.raw(collection).all().mapNotNull { record ->
      val text = record.fields.filterKeys { it !in setOf("data", "image", "cover", "thumbnail") }
        .values.filterIsInstance<JsonPrimitive>().filter { it.isString && it.content.length < 10000 }
        .joinToString(" ") { it.content }
      if (!terms.all { it in text.lowercase() }) return@mapNotNull null
      SearchResult("${ctx.pluginId}/$collection/${record.id}", record.label(), label()) {
        ctx.nav.openPlugin(ctx.pluginId, route(record.id))
      }
    }.take(50)
  }
}

fun RawRecord.label(): String = listOf("title", "name", "text", "label", "word", "comment", "category", "description")
  .firstNotNullOfOrNull { (fields[it] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
  ?: id

class CollectionDaySource(
  private val ctx: PluginContext,
  private val collection: String,
  private val label: () -> String,
  private val route: (String) -> String? = { null },
  private val describe: (RawRecord) -> String = { it.label() },
  private val page: String? = null
) : DaySummarySource {
  override suspend fun entries(date: LocalDate): List<DayEntry> = ctx.data.raw(collection).all()
    .filter { (it.fields["date"] as? JsonPrimitive)?.contentOrNull == date.toString() || it.id == date.toString() }
    .map { record -> DayEntry("${ctx.pluginId}/$collection/${record.id}", label(), describe(record)) {
      ctx.nav.openPlugin(ctx.pluginId, route(record.id), page)
    } }
}
