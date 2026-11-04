package dev.akkraizen.berill.core.today

import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.DeadlineSource
import dev.akkraizen.berill.api.TodayItem
import dev.akkraizen.berill.api.TodayKind
import dev.akkraizen.berill.api.TodaySource
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.log.AppLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

class TodayFeed(private val core: BerillCore) {
  @OptIn(ExperimentalCoroutinesApi::class)
  fun observe(date: LocalDate): Flow<List<TodayItem>> = core.plugins.enabled.flatMapLatest { plugins ->
    val flows = plugins.flatMap { plugin -> sources(plugin, date) }
    if (flows.isEmpty()) flowOf(emptyList()) else combine(flows) { parts -> sortToday(parts.flatMap { it }) }
  }

  private fun sources(plugin: BerillPlugin, date: LocalDate): List<Flow<List<TodayItem>>> {
    val ctx = runCatching { core.plugins.context(plugin) }.getOrNull() ?: return emptyList()
    val capabilities = try { plugin.provides(ctx) } catch (e: Exception) {
      AppLog.error("today", plugin.id, e)
      return emptyList()
    }
    val own = capabilities.filterIsInstance<TodaySource>()
    val flows = if (own.isNotEmpty()) own.map { it.today(date) }
    else capabilities.filterIsInstance<DeadlineSource>().map { source ->
      source.deadlines().map { list ->
        list.filter { it.date.daysUntil(date) <= OVERDUE_DAYS && it.date <= date.plus(AHEAD_DAYS, DateTimeUnit.DAY) }.map { deadline ->
          TodayItem(
            id = "${deadline.pluginId}/deadline/${deadline.title}/${deadline.date}",
            title = deadline.title,
            kind = TodayKind.DEADLINE,
            pluginId = deadline.pluginId,
            due = deadline.date,
            overdue = deadline.date < date
          ) { core.nav.openPlugin(deadline.pluginId, deadline.route) }
        }
      }
    }
    return flows.map { flow ->
      flow.catch { e ->
        AppLog.error("today", plugin.id, e)
        emit(emptyList())
      }
    }
  }

  private companion object {
    const val OVERDUE_DAYS = 14
    const val AHEAD_DAYS = 3
  }
}

fun sortToday(items: List<TodayItem>): List<TodayItem> =
  items.sortedWith(
    compareBy<TodayItem> { it.done == true }
      .thenByDescending { it.overdue }
      .thenBy { it.time == null }
      .thenBy { it.time ?: "" }
      .thenBy { it.due }
      .thenBy { it.kind.ordinal }
      .thenBy { it.title.lowercase() }
  )
