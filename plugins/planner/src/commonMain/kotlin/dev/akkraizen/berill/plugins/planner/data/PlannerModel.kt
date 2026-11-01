package dev.akkraizen.berill.plugins.planner.data

import dev.akkraizen.berill.api.Deadline
import dev.akkraizen.berill.api.DeadlineSource
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.all
import dev.akkraizen.berill.api.observe
import dev.akkraizen.berill.api.observeBoolean
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.planner.PlannerPlugin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.datetime.LocalDate

@Serializable
data class PlannerTask(
  val text: String = "",
  val date: String? = null,
  val week: String? = null,
  val done: Boolean = false,
  val order: Long? = null,
  @SerialName("deferred_dates") val deferredDates: List<String> = emptyList()
) {
  fun rescheduled(to: LocalDate): PlannerTask {
    val target = to.toString()
    if (date == target) return this
    return copy(date = target, done = false, deferredDates = (deferredDates + listOfNotNull(date)).distinct())
  }
}

data class PlannerDayTotals(val planned: Int, val completed: Int, val deferred: Int) {
  val unfinished: Int get() = planned - completed
}

fun plannerDayTotals(tasks: List<PlannerTask>, day: LocalDate): PlannerDayTotals {
  val date = day.toString()
  return PlannerDayTotals(
    planned = tasks.count { it.date == date || date in it.deferredDates },
    completed = tasks.count { it.date == date && it.done },
    deferred = tasks.count { it.date != date && date in it.deferredDates }
  )
}

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

internal const val SHOW_DEADLINES = "showDeadlines"

internal class PlannerData(val ctx: PluginContext) {
  val tasks = ctx.data.collection(PlannerPlugin.TASKS, PlannerTask.serializer())
  val weeks = ctx.data.collection(PlannerPlugin.WEEKS, WeekNotes.serializer())

  fun addDayTask(date: String, text: String) {
    if (text.isBlank()) return
    tasks.save(ctx.data.newId(), PlannerTask(text = normalizeInput(text), date = date, order = nowMillis()))
  }

  fun edit(id: String, transform: (PlannerTask) -> PlannerTask) {
    val current = tasks.get(id) ?: return
    tasks.save(id, transform(current))
  }

  fun addGoal(week: String, text: String) {
    if (text.isBlank()) return
    tasks.save(ctx.data.newId(), PlannerTask(text = text.trim(), week = week, order = nowMillis()))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun deadlines(): Flow<List<Deadline>> =
    combine(ctx.settings.observeBoolean(SHOW_DEADLINES, true), ctx.capabilities.observe<DeadlineSource>()) { show, sources ->
      if (show) sources else emptyList()
    }.flatMapLatest { sources ->
      if (sources.isEmpty())
        flowOf(emptyList())
      else
        combine(sources.map { it.deadlines() }) { lists -> lists.flatMap { it } }
    }
}
