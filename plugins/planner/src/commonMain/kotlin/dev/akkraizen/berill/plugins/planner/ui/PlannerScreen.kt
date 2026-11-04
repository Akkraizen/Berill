package dev.akkraizen.berill.plugins.planner.ui

import dev.akkraizen.berill.api.ui.layout.TAB_STATISTICS
import dev.akkraizen.berill.api.ui.layout.TabbedPluginList
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.interaction.SwipeDirection
import dev.akkraizen.berill.api.ui.interaction.swipeArea
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.DAYS_SHORT
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.isoWeek
import dev.akkraizen.berill.api.ui.isoWeekId
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.mondayOfWeek
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.weekdayIndex
import dev.akkraizen.berill.plugins.planner.data.plannerDayTotals
import dev.akkraizen.berill.plugins.planner.data.PlannerData
import dev.akkraizen.berill.plugins.planner.data.parse
import dev.akkraizen.berill.plugins.planner.data.taskOrder
import dev.akkraizen.berill.plugins.planner.i18n.PlannerText

@Composable
internal fun PlannerScreen(ctx: PluginContext) {
  val data = remember(ctx) { PlannerData(ctx) }
  val all by remember(data) { data.tasks.observe() }.collectAsState(emptyList())
  val deadlines by remember(data) { data.deadlines() }.collectAsState(emptyList())
  val now = today()
  var weekOffset by rememberSaveable { mutableStateOf(0) }
  var weekView by rememberSaveable { mutableStateOf(false) }
  var selectedIndex by rememberSaveable { mutableStateOf(now.weekdayIndex) }

  val monday = now.mondayOfWeek().plusDays(weekOffset * 7)
  val days = (0..6).map { monday.plusDays(it) }
  val weekId = monday.isoWeekId()
  val isCurrentWeek = weekOffset == 0
  val selected = days[selectedIndex.coerceIn(0, 6)]
  val byDate = all.filter { it.value.date != null }.groupBy { it.value.date!! }
  val goals = all.filter { it.value.week == weekId }.sortedWith(taskOrder)
  val tails = days
    .filter { it < now }
    .flatMap { d -> byDate[d.toString()].orEmpty().filter { !it.value.done }.map { d to it } }

  val stepDay: (SwipeDirection) -> Unit = { direction ->
    val next = selectedIndex.coerceIn(0, 6) + if (direction == SwipeDirection.NEXT) 1 else -1
    when {
      next > 6 -> { weekOffset++; selectedIndex = 0 }
      next < 0 -> { weekOffset--; selectedIndex = 6 }
      else -> selectedIndex = next
    }
  }
  val stepWeek: (SwipeDirection) -> Unit = { direction -> weekOffset += if (direction == SwipeDirection.NEXT) 1 else -1 }

  TabbedPluginList { tab ->
    if (tab == TAB_STATISTICS) {
      item { PlannerStatistics(all.map { it.value }, days) }
      return@TabbedPluginList
    }
    item {
      FlowRow(
        Modifier.fillMaxWidth().swipeArea(onSwipe = stepWeek),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { weekOffset-- }) {
            Icon(BerillIcons.Prev, contentDescription = PlannerText.previousWeek())
          }
          Column(
            Modifier.interactive(bleed = 6.dp) {
              weekOffset = 0
              selectedIndex = now.weekdayIndex
            }
          ) {
            Text(
              if (isCurrentWeek)
                PlannerText.thisWeek()
              else
                PlannerText.weekNumber(monday.isoWeek().second)(),
              style = MaterialTheme.typography.titleLarge
            )
            Text(
              "${monday.dayMonthShort()} – ${days.last().dayMonthShort()}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          IconButton(onClick = { weekOffset++ }) {
            Icon(BerillIcons.Next, contentDescription = PlannerText.nextWeek())
          }
        }
        ChoiceChips(
          listOf(false, true),
          weekView,
          { if (it) PlannerText.week() else PlannerText.day() },
          { weekView = it },
          icon = { if (it) Icons.Outlined.ViewWeek else Icons.Outlined.ViewDay }
        )
      }
    }
    if (!weekView) {
      item {
        FlowRow(
          Modifier.fillMaxWidth().swipeArea(onSwipe = stepDay),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          days.forEachIndexed { i, d ->
            val undone = plannerDayTotals(all.map { it.value }, d).unfinished
            BerillChip(
              selected = i == selectedIndex,
              onClick = { selectedIndex = i },
              label = {
                Text("${DAYS_SHORT[i]} ${d.day}" + if (undone > 0) " · $undone" else "")
              },
              border = if (d == now)
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
              else
                null
            )
          }
        }
      }
      item {
        TwoPane(
          left = {
            AnimatedContent(
              targetState = selected,
              modifier = Modifier.swipeArea(feedback = false, onSwipe = stepDay),
              transitionSpec = { BerillMotion.slide(forward = targetState > initialState) },
              label = "day"
            ) { day ->
              DayCard(data, day, all, deadlines, compact = false)
            }
          },
          right = {
            GoalsCard(data, weekId, goals)
            if (tails.isNotEmpty())
              SectionCard(
                PlannerText.notDoneEarlier(),
                Icons.Outlined.HourglassBottom,
                trailing = if (isCurrentWeek) {
                  {
                    TextButton(
                      onClick = {
                        tails.forEach { (_, t) ->
                          data.edit(t.id) { it.rescheduled(now) }
                        }
                      }
                    ) {
                      Text(PlannerText.allToToday())
                    }
                  }
                } else
                  null
              ) {
                dev.akkraizen.berill.api.ui.layout.BoundedList {
                  tails.forEach { (d, t) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        "${DAYS_SHORT[d.weekdayIndex]}, ${parse(t.value.text).title}",
                        modifier = Modifier.weight(1f)
                      )
                      if (isCurrentWeek)
                        TextButton(onClick = { data.edit(t.id) { it.rescheduled(now) } }) {
                          Ic(BerillIcons.Forward, size = 16.dp)
                          Spacer(Modifier.width(4.dp))
                          Text(PlannerText.toToday())
                        }
                    }
                  }
                }

              }
            WeekSummary(data, weekId)
          }
        )
      }
    } else {
      item {
        BoxWithConstraints(Modifier.fillMaxWidth().swipeArea(onSwipe = stepWeek)) {
          val cols = (maxWidth / 230.dp).toInt().coerceIn(1, 7)
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            days.chunked(cols).forEach { row ->
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d ->
                  Box(Modifier.weight(1f)) { DayCard(data, d, all, deadlines, compact = true) }
                }
                repeat(cols - row.size) { Box(Modifier.weight(1f)) }
              }
            }
          }
        }
      }
      item { GoalsCard(data, weekId, goals) }
      item { WeekSummary(data, weekId) }
    }
  }
}
