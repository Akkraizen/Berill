package dev.akkraizen.berill.plugins.planner

import dev.akkraizen.berill.api.Item
import kotlinx.serialization.Serializable

@Serializable
data class PlannerTask(
  val text: String = "",
  val date: String? = null,
  val week: String? = null,
  val done: Boolean = false,
  val order: Long? = null
)

@Serializable
data class WeekNotes(val summary: String = "")

private val TIME = Regex("""^(\d{1,2}):(\d{2})\s+(.*)$""")

data class ParsedTask(val time: String?, val title: String, val important: Boolean)

fun parse(text: String): ParsedTask {
  val m = TIME.matchEntire(text.trim())
  val time = m?.let { "${it.groupValues[1].padStart(2, '0')}:${it.groupValues[2]}" }
  val title = m?.groupValues?.get(3) ?: text.trim()
  return ParsedTask(time, title, text.contains('!'))
}

fun normalizeInput(text: String): String {
  val p = parse(text)
  return if (p.time != null) "${p.time} ${p.title}" else text.trim()
}

private fun importedIndex(id: String): Long = id.substringAfterLast('/').substringBefore('-').toLongOrNull() ?: Long.MAX_VALUE

val taskOrder: Comparator<Item<PlannerTask>> = compareBy<Item<PlannerTask>> { it.value.done }
  .thenBy { parse(it.value.text).time == null }
  .thenBy { parse(it.value.text).time ?: "" }
  .thenBy { !parse(it.value.text).important }
  .thenBy { it.value.order ?: importedIndex(it.id) }
