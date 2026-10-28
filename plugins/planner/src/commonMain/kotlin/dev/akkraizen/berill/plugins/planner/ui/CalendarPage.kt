package dev.akkraizen.berill.plugins.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Deadline
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.DAYS_FULL
import dev.akkraizen.berill.api.ui.DAYS_SHORT
import dev.akkraizen.berill.api.ui.MONTHS_NOM
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.localDateTime
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.weekdayIndex
import dev.akkraizen.berill.plugins.planner.data.CalendarData
import dev.akkraizen.berill.plugins.planner.data.CalendarEvent
import dev.akkraizen.berill.plugins.planner.data.DaySegment
import dev.akkraizen.berill.plugins.planner.data.GoogleCalendarInfo
import dev.akkraizen.berill.plugins.planner.data.GoogleCalendarSync
import dev.akkraizen.berill.plugins.planner.data.PlannerData
import dev.akkraizen.berill.plugins.planner.data.PlannerTask
import dev.akkraizen.berill.plugins.planner.data.daySegments
import dev.akkraizen.berill.plugins.planner.data.eventsOn
import dev.akkraizen.berill.plugins.planner.data.hhmm
import dev.akkraizen.berill.plugins.planner.data.minutesOf
import dev.akkraizen.berill.plugins.planner.data.monthGrid
import dev.akkraizen.berill.plugins.planner.data.parse
import dev.akkraizen.berill.plugins.planner.data.parseTime
import dev.akkraizen.berill.plugins.planner.data.timeOfMinutes
import dev.akkraizen.berill.plugins.planner.i18n.CalendarText
import dev.akkraizen.berill.plugins.planner.i18n.PlannerText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal const val DAY_ROUTE = "day/"

private val HOUR = 56.dp
private val GUTTER = 48.dp
private const val TASK_PREFIX = "task:"

private class CalendarState(
  val ctx: PluginContext,
  val events: List<Item<CalendarEvent>>,
  val tasks: List<Item<PlannerTask>>,
  val deadlines: List<Deadline>,
  val calendars: List<GoogleCalendarInfo>,
  val google: GoogleCalendarSync,
  val data: CalendarData,
  val planner: PlannerData
) {
  fun colorOf(event: CalendarEvent): Color? =
    calendars.firstOrNull { it.id == event.calendarId }?.color?.let(::parseHex)

  fun tasksOn(day: LocalDate): List<Item<PlannerTask>> = tasks.filter { it.value.date == day.toString() }

  fun deadlinesOn(day: LocalDate): List<Deadline> = deadlines.filter { it.date == day }
}

@Composable
private fun rememberCalendarState(ctx: PluginContext): CalendarState {
  val data = remember(ctx) { CalendarData(ctx) }
  val planner = remember(ctx) { PlannerData(ctx) }
  val google = remember(ctx) { GoogleCalendarSync.of(ctx) }
  val events by remember(data) { data.observeVisible() }.collectAsState(emptyList())
  val tasks by remember(planner) { planner.tasks.observe() }.collectAsState(emptyList())
  val deadlines by remember(planner) { planner.deadlines() }.collectAsState(emptyList())
  val calendars by remember(google) { google.observeCalendars() }.collectAsState(google.calendars())
  return CalendarState(ctx, events, tasks, deadlines, calendars, google, data, planner)
}

internal fun parseHex(text: String): Color? {
  val hex = text.trim().removePrefix("#")
  if (hex.length != 6) return null
  val v = hex.toLongOrNull(16) ?: return null
  return Color(0xFF000000 or v)
}

@Composable
internal fun CalendarPage(ctx: PluginContext) {
  val route by ctx.nav.route.collectAsState()
  val state = rememberCalendarState(ctx)
  val connected by state.google.connected.collectAsState(state.google.isConnected())
  LaunchedEffect(connected) {
    while (connected) {
      runCatching { state.google.sync() }
      delay(5 * 60_000L)
    }
  }
  var editing by remember { mutableStateOf<Editing?>(null) }
  AnimatedRoute(route) { r ->
    val day = r?.takeIf { it.startsWith(DAY_ROUTE) }?.removePrefix(DAY_ROUTE)?.toDateOrNull()
    if (day != null)
      DayView(state, day, connected) { edit -> editing = edit }
    else
      MonthView(state, connected) { edit -> editing = edit }
  }
  editing?.let { (id, event, occurrence) ->
    EventDialog(
      initial = event,
      isNew = id == null,
      occurrence = occurrence,
      calendars = state.calendars,
      defaultCalendar = if (connected) state.google.defaultCalendar() else null,
      onDismiss = { editing = null },
      onSave = {
        state.data.save(id, it)
        state.google.scheduleSync()
        editing = null
      },
      onDelete = {
        if (id != null) state.data.delete(id)
        state.google.scheduleSync()
        editing = null
      },
      onSkip = {
        if (id != null && occurrence != null) state.data.skipOccurrence(id, occurrence)
        state.google.scheduleSync()
        editing = null
      }
    )
  }
}

private data class Editing(val id: String?, val event: CalendarEvent, val occurrence: LocalDate? = null)

private fun newEvent(day: LocalDate, hour: Int? = null): Editing {
  val h = hour ?: 9
  return Editing(
    null,
    CalendarEvent(
      date = day.toString(),
      start = timeOfMinutes(h * 60).hhmm(),
      end = if (h >= 23) "23:59" else timeOfMinutes((h + 1) * 60).hhmm()
    )
  )
}

internal fun CalendarEvent.label(day: LocalDate): String {
  val age = if (isBirthday) ageOn(day) else null
  return if (age != null && age > 0) "$title, $age" else title
}

@Composable
private fun SyncButton(state: CalendarState) {
  val sync by state.google.state.collectAsState()
  val scope = rememberCoroutineScope()
  IconButton(enabled = !sync.running, onClick = { scope.launch { runCatching { state.google.sync() } } }) {
    if (sync.running)
      CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    else
      Icon(
        Icons.Outlined.Sync,
        contentDescription = CalendarText.syncNow(),
        tint = if (sync.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
      )
  }
}

@Composable
private fun MonthView(state: CalendarState, connected: Boolean, edit: (Editing) -> Unit) {
  val now = today()
  var month by rememberSaveable { mutableStateOf(now.year * 12 + now.month.ordinal) }
  val year = month / 12
  val monthNumber = month % 12 + 1
  PluginList {
    item {
      FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { month-- }) { Icon(BerillIcons.Prev, contentDescription = UiText.previousMonth()) }
          Text(
            "${MONTHS_NOM[monthNumber - 1]} $year",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.interactive(bleed = 6.dp) { month = now.year * 12 + now.month.ordinal }
          )
          IconButton(onClick = { month++ }) { Icon(BerillIcons.Next, contentDescription = UiText.nextMonth()) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (connected) SyncButton(state)
          FilledTonalButton(onClick = { edit(newEvent(now)) }) {
            Ic(BerillIcons.Add, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(CalendarText.event())
          }
        }
      }
    }
    if (!connected && state.google.isConfigured()) item { GoogleSignInButton(state.google, compact = true) }
    item { MonthGrid(state, year, monthNumber, now) }
  }
}

private class CellEntry(val title: String, val color: Color, val done: Boolean = false)

@Composable
private fun MonthGrid(state: CalendarState, year: Int, month: Int, now: LocalDate) {
  val colors = MaterialTheme.colorScheme
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val wide = maxWidth / 7 >= 88.dp
    Column(
      Modifier.clip(MaterialTheme.shapes.large).border(1.dp, colors.outlineVariant, MaterialTheme.shapes.large)
    ) {
      Row(Modifier.fillMaxWidth().background(colors.surfaceContainer).padding(vertical = 6.dp)) {
        DAYS_SHORT.forEach {
          Text(
            it,
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
          )
        }
      }
      monthGrid(year, month).chunked(7).forEach { week ->
        HorizontalDivider(color = colors.outlineVariant)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
          week.forEachIndexed { i, day ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.outlineVariant))
            val entries = buildList {
              eventsOn(state.events, day).forEach {
                val fallback = if (it.value.isBirthday) colors.tertiary else colors.primary
                add(CellEntry(it.value.label(day), state.colorOf(it.value) ?: fallback))
              }
              state.deadlinesOn(day).forEach { add(CellEntry(it.title, colors.error)) }
              state.tasksOn(day).forEach { add(CellEntry(parse(it.value.text).title, colors.outline, it.value.done)) }
            }
            DayCell(
              day,
              inMonth = day.month.ordinal + 1 == month,
              isToday = day == now,
              entries = entries,
              wide = wide,
              modifier = Modifier.weight(1f)
            ) { state.ctx.nav.open(DAY_ROUTE + day) }
          }
        }
      }
    }
  }
}

@Composable
private fun DayCell(
  day: LocalDate,
  inMonth: Boolean,
  isToday: Boolean,
  entries: List<CellEntry>,
  wide: Boolean,
  modifier: Modifier,
  onClick: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val alpha = if (inMonth) 1f else 0.45f
  Column(
    modifier
      .heightIn(min = if (wide) 104.dp else 60.dp)
      .interactive(onClick = onClick)
      .padding(4.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    Box(
      Modifier
        .size(24.dp)
        .clip(CircleShape)
        .background(if (isToday) colors.primary else Color.Transparent),
      contentAlignment = Alignment.Center
    ) {
      Text(
        "${day.day}",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        color = (if (isToday) colors.onPrimary else colors.onSurface).copy(alpha = alpha)
      )
    }
    if (wide) {
      entries.take(3).forEach { e ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(e.color.copy(alpha = alpha)))
          Spacer(Modifier.width(4.dp))
          Text(
            e.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (e.done) TextDecoration.LineThrough else null,
            color = colors.onSurface.copy(alpha = alpha)
          )
        }
      }
      if (entries.size > 3)
        Text("+${entries.size - 3}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    } else if (entries.isNotEmpty()) {
      Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        entries.take(3).forEach { e ->
          Box(Modifier.size(6.dp).clip(CircleShape).background(e.color.copy(alpha = if (e.done) 0.4f * alpha else alpha)))
        }
        if (entries.size > 3)
          Text("+", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun DayView(state: CalendarState, day: LocalDate, connected: Boolean, edit: (Editing) -> Unit) {
  val nav = state.ctx.nav
  val colors = MaterialTheme.colorScheme
  val events = eventsOn(state.events, day)
  val allDay = events.filter { it.value.allDay }
  val tasks = state.tasksOn(day)
  val untimed = tasks.filter { parse(it.value.text).time == null }
  val deadlines = state.deadlinesOn(day)
  val taskItems = tasks.mapNotNull { item ->
    val p = parse(item.value.text)
    val t = parseTime(p.time) ?: return@mapNotNull null
    val start = minutesOf(t)
    Item(
      TASK_PREFIX + item.id,
      CalendarEvent(
        title = p.title,
        date = day.toString(),
        start = t.hhmm(),
        end = timeOfMinutes(minOf(start + 30, 24 * 60 - 1)).hhmm()
      )
    )
  }
  val segments = daySegments(events + taskItems, day)
  val doneTasks = tasks.filter { it.value.done }.map { TASK_PREFIX + it.id }.toSet()

  Column(Modifier.fillMaxSize()) {
    Column(
      Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { nav.open(DAY_ROUTE + day.plusDays(-1)) }) {
            Icon(BerillIcons.Prev, contentDescription = CalendarText.previousDay())
          }
          Column(Modifier.interactive(bleed = 6.dp) { nav.open(DAY_ROUTE + today()) }) {
            Text(DAYS_FULL[day.weekdayIndex], style = MaterialTheme.typography.titleLarge)
            Text("${day.dayMonth()} ${day.year}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
          }
          IconButton(onClick = { nav.open(DAY_ROUTE + day.plusDays(1)) }) {
            Icon(BerillIcons.Next, contentDescription = CalendarText.nextDay())
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (connected) SyncButton(state)
          FilledTonalButton(onClick = { edit(newEvent(day)) }) {
            Ic(BerillIcons.Add, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(CalendarText.event())
          }
        }
      }
      if (allDay.isNotEmpty() || untimed.isNotEmpty() || deadlines.isNotEmpty())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          allDay.forEach { item ->
            val age = item.value.takeIf { it.isBirthday }?.ageOn(day)?.takeIf { it > 0 }
            BerillChip(
              onClick = { state.events.firstOrNull { it.id == item.id }?.let { edit(Editing(it.id, it.value, day)) } },
              label = {
                Text(
                  if (age != null) "${item.value.title} · ${CalendarText.turns(age)()}" else item.value.title,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              },
              leadingIcon = {
                if (item.value.isBirthday)
                  Ic(Icons.Outlined.Cake, size = 16.dp, tint = state.colorOf(item.value) ?: colors.tertiary)
                else
                  Box(Modifier.size(8.dp).clip(CircleShape).background(state.colorOf(item.value) ?: colors.primary))
              }
            )
          }
          deadlines.forEach { d ->
            BerillChip(
              onClick = { nav.openPlugin(d.pluginId, d.route) },
              label = { Text(PlannerText.deadline(d.title)(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
              leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(colors.error)) }
            )
          }
          untimed.forEach { item ->
            BerillChip(
              selected = item.value.done,
              onClick = { state.planner.edit(item.id) { it.copy(done = !it.done) } },
              label = {
                Text(
                  parse(item.value.text).title,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  textDecoration = if (item.value.done) TextDecoration.LineThrough else null
                )
              },
              leadingIcon = if (item.value.done) {
                { Ic(BerillIcons.Check, size = 16.dp) }
              } else
                null
            )
          }
        }
    }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val isToday = day == today()
    LaunchedEffect(day) {
      val minutes = if (isToday) minutesOf(localDateTime(nowMillis()).time) - 60 else 7 * 60
      scroll.scrollTo(with(density) { (HOUR * (minutes.coerceAtLeast(0) / 60f)).roundToPx() })
    }
    Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)) {
      HourGrid(
        segments = segments,
        isToday = isToday,
        done = doneTasks,
        colorOf = { state.colorOf(it) },
        onSlot = { hour -> edit(newEvent(day, hour)) },
        onOpen = { seg ->
          if (seg.id.startsWith(TASK_PREFIX))
            state.planner.edit(seg.id.removePrefix(TASK_PREFIX)) { it.copy(done = !it.done) }
          else
            state.events.firstOrNull { it.id == seg.id }?.let { edit(Editing(it.id, it.value, day)) }
        }
      )
    }
  }
}

@Composable
private fun HourGrid(
  segments: List<DaySegment>,
  isToday: Boolean,
  done: Set<String>,
  colorOf: (CalendarEvent) -> Color?,
  onSlot: (Int) -> Unit,
  onOpen: (DaySegment) -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val hourPx = with(density) { HOUR.toPx() }
  Box(Modifier.fillMaxWidth().height(HOUR * 24)) {
    Column(Modifier.fillMaxWidth()) {
      repeat(24) { h ->
        Row(Modifier.height(HOUR)) {
          Text(
            timeOfMinutes(h * 60).hhmm(),
            Modifier.width(GUTTER).offset(y = (-7).dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
          )
          HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
        }
      }
    }
    Box(
      Modifier
        .padding(start = GUTTER)
        .fillMaxSize()
        .pointerInput(Unit) { detectTapGestures { onSlot((it.y / hourPx).toInt().coerceIn(0, 23)) } }
    )
    BoxWithConstraints(Modifier.padding(start = GUTTER).fillMaxSize()) {
      val width = maxWidth
      segments.forEach { seg -> EventBlock(seg, width, colorOf(seg.event), seg.id in done, onOpen) }
    }
    if (isToday) {
      val minutes by produceState(minutesOf(localDateTime(nowMillis()).time)) {
        while (true) {
          delay(30_000)
          value = minutesOf(localDateTime(nowMillis()).time)
        }
      }
      Row(
        Modifier.fillMaxWidth().offset(y = HOUR * (minutes / 60f) - 4.dp).padding(start = GUTTER - 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(colors.error))
        Box(Modifier.weight(1f).height(2.dp).background(colors.error))
      }
    }
  }
}

@Composable
private fun EventBlock(seg: DaySegment, width: Dp, color: Color?, done: Boolean, onOpen: (DaySegment) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val task = seg.id.startsWith(TASK_PREFIX)
  val accent = when {
    task -> colors.outline
    color != null -> color
    else -> colors.primary
  }
  val columnWidth = width / seg.columns
  val height = HOUR * ((seg.endMinute - seg.startMinute) / 60f)
  Surface(
    onClick = { onOpen(seg) },
    modifier = Modifier
      .offset(x = columnWidth * seg.column, y = HOUR * (seg.startMinute / 60f))
      .width(columnWidth - 2.dp)
      .height(height - 2.dp),
    shape = RoundedCornerShape(6.dp),
    color = if (task) colors.surface else accent.copy(alpha = 0.22f),
    border = if (task) BorderStroke(1.dp, colors.outlineVariant) else null
  ) {
    Row {
      Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
      Column(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(
          seg.event.title,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = if (height >= 40.dp) 2 else 1,
          overflow = TextOverflow.Ellipsis,
          textDecoration = if (done) TextDecoration.LineThrough else null,
          color = colors.onSurface.copy(alpha = if (done) 0.6f else 1f)
        )
        if (height >= 40.dp && !task)
          Text(
            "${seg.event.start} – ${seg.event.end}" + seg.event.location.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.onSurfaceVariant
          )
      }
    }
  }
}
