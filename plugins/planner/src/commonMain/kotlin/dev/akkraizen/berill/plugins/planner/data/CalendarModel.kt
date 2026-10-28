package dev.akkraizen.berill.plugins.planner.data

import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.mondayOfWeek
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.planner.PlannerPlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
  val title: String = "",
  val date: String = "",
  val endDate: String? = null,
  val start: String? = null,
  val end: String? = null,
  val location: String = "",
  val notes: String = "",
  val calendarId: String? = null,
  val googleId: String? = null,
  val etag: String? = null,
  val dirty: Boolean = false,
  val deleted: Boolean = false,
  val updatedAt: Long = 0
) {
  val allDay: Boolean
    get() = start == null

  val firstDay: LocalDate?
    get() = date.toDateOrNull()

  val lastDay: LocalDate?
    get() {
      val first = firstDay ?: return null
      val last = endDate.toDateOrNull() ?: first
      if (!allDay && last > first && parseTime(end) == LocalTime(0, 0)) return last.plusDays(-1)
      return if (last < first) first else last
    }

  fun occursOn(day: LocalDate): Boolean {
    val first = firstDay ?: return false
    val last = lastDay ?: return false
    return day in first..last
  }

  fun startAt(): LocalDateTime? {
    val d = firstDay ?: return null
    return LocalDateTime(d, parseTime(start) ?: LocalTime(0, 0))
  }

  fun endAt(): LocalDateTime? {
    val d = endDate.toDateOrNull() ?: firstDay ?: return null
    val t = parseTime(end) ?: return startAt()?.let { plusMinutes(it, 60) }
    return LocalDateTime(d, t)
  }
}

fun parseTime(text: String?): LocalTime? {
  val parts = text?.trim()?.split(':') ?: return null
  if (parts.size != 2) return null
  val h = parts[0].toIntOrNull() ?: return null
  val m = parts[1].toIntOrNull() ?: return null
  if (h !in 0..23 || m !in 0..59) return null
  return LocalTime(h, m)
}

fun LocalTime.hhmm(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

fun minutesOf(time: LocalTime): Int = time.hour * 60 + time.minute

fun timeOfMinutes(minutes: Int): LocalTime {
  val m = minutes.coerceIn(0, 24 * 60 - 1)
  return LocalTime(m / 60, m % 60)
}

fun plusMinutes(at: LocalDateTime, minutes: Int): LocalDateTime {
  val total = minutesOf(at.time) + minutes
  val days = total.floorDiv(24 * 60)
  val rest = total.mod(24 * 60)
  return LocalDateTime(at.date.plusDays(days), timeOfMinutes(rest))
}

fun monthGrid(year: Int, month: Int): List<LocalDate> {
  val first = LocalDate(year, month, 1).mondayOfWeek()
  return List(42) { first.plusDays(it) }
}

data class DaySegment(
  val id: String,
  val event: CalendarEvent,
  val startMinute: Int,
  val endMinute: Int,
  val column: Int = 0,
  val columns: Int = 1
)

fun daySegments(events: List<Item<CalendarEvent>>, day: LocalDate, minLength: Int = 15): List<DaySegment> {
  val raw = events
    .filter { !it.value.deleted && !it.value.allDay && it.value.occursOn(day) }
    .mapNotNull { item ->
      val s = item.value.startAt() ?: return@mapNotNull null
      val e = item.value.endAt() ?: return@mapNotNull null
      val from = if (s.date < day) 0 else minutesOf(s.time)
      val to = if (e.date > day) 24 * 60 else minutesOf(e.time)
      val end = maxOf(to, from + minLength).coerceAtMost(24 * 60)
      DaySegment(item.id, item.value, from.coerceAtMost(24 * 60 - minLength), end)
    }
    .sortedWith(compareBy({ it.startMinute }, { -it.endMinute }, { it.event.title }))
  val result = ArrayList<DaySegment>()
  var cluster = ArrayList<DaySegment>()
  var clusterEnd = -1
  fun flush() {
    if (cluster.isEmpty()) return
    val ends = ArrayList<Int>()
    val placed = cluster.map { seg ->
      val free = ends.indexOfFirst { it <= seg.startMinute }
      val col = if (free >= 0) free else ends.size
      if (col == ends.size) ends.add(seg.endMinute) else ends[col] = seg.endMinute
      seg.copy(column = col)
    }
    placed.forEach { result.add(it.copy(columns = ends.size)) }
    cluster = ArrayList()
  }
  raw.forEach { seg ->
    if (seg.startMinute >= clusterEnd) flush()
    cluster.add(seg)
    clusterEnd = maxOf(clusterEnd, seg.endMinute)
  }
  flush()
  return result
}

fun eventsOn(events: List<Item<CalendarEvent>>, day: LocalDate): List<Item<CalendarEvent>> =
  events
    .filter { !it.value.deleted && it.value.occursOn(day) }
    .sortedWith(compareBy({ !it.value.allDay }, { it.value.startAt() }, { it.value.title }))

internal class CalendarData(val ctx: PluginContext) {
  val events = ctx.data.collection(PlannerPlugin.EVENTS, CalendarEvent.serializer())

  fun observeVisible(): Flow<List<Item<CalendarEvent>>> = events.observe().map { all -> all.filter { !it.value.deleted } }

  fun save(id: String?, event: CalendarEvent): String {
    val key = id ?: ctx.data.newId()
    events.save(key, event.copy(dirty = true, updatedAt = nowMillis()))
    return key
  }

  fun delete(id: String) {
    val current = events.get(id) ?: return
    if (current.googleId == null)
      events.delete(id)
    else
      events.save(id, current.copy(deleted = true, dirty = true, updatedAt = nowMillis()))
  }
}
