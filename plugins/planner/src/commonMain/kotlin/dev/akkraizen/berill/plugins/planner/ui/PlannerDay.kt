package dev.akkraizen.berill.plugins.planner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Deadline
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.DAYS_FULL
import dev.akkraizen.berill.api.ui.DAYS_SHORT
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.weekdayIndex
import dev.akkraizen.berill.plugins.planner.data.PlannerData
import dev.akkraizen.berill.plugins.planner.data.PlannerTask
import dev.akkraizen.berill.plugins.planner.data.parse
import dev.akkraizen.berill.plugins.planner.data.taskOrder
import dev.akkraizen.berill.plugins.planner.i18n.PlannerText
import kotlinx.datetime.LocalDate

@Composable
internal fun DayCard(
  data: PlannerData,
  date: LocalDate,
  all: List<Item<PlannerTask>>,
  deadlines: List<Deadline>,
  compact: Boolean,
  showHeader: Boolean = true
) {
  val tasks = all.filter { it.value.date == date.toString() }.sortedWith(taskOrder)
  val dayDeadlines = deadlines.filter { it.date == date }
  val done = tasks.count { it.value.done }
  val isToday = date == today()
  var input by rememberDraft("planner/day/$date")

  Surface(
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = if (isToday && showHeader)
      BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else
      null,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      Modifier.animateContentSize(BerillMotion.spatial()).padding(if (compact) 12.dp else 18.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (showHeader)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            if (compact) DAYS_SHORT[date.weekdayIndex] else DAYS_FULL[date.weekdayIndex],
            style = if (compact)
              MaterialTheme.typography.titleSmall
            else
              MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
          )
          Text(
            "${date.dayMonthShort()} · $done/${tasks.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      if (tasks.isNotEmpty()) ProgressLine(done / tasks.size.toFloat())
      dayDeadlines.forEach { d ->
        Row(
          Modifier.interactive(bleed = 6.dp) { data.ctx.nav.openPlugin(d.pluginId, d.route) },
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Ic(Icons.Outlined.Alarm, size = 18.dp, tint = MaterialTheme.colorScheme.error)
          Text(
            PlannerText.deadline(d.title)(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
      if (tasks.isEmpty() && dayDeadlines.isEmpty()) EmptyHint(PlannerText.empty())
      tasks.forEach { item ->
        TaskRow(
          item.value,
          compact = compact,
          onToggle = { data.edit(item.id) { it.copy(done = !it.done) } },
          onNextDay = { data.edit(item.id) { it.copy(date = date.plusDays(1).toString()) } },
          onDelete = { data.tasks.delete(item.id) }
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        BerillTextField(
          input,
          { input = it },
          placeholder = {
            Text(
              if (compact) PlannerText.taskShort() else PlannerText.newTaskHint(),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          },
          singleLine = true,
          modifier = Modifier.weight(1f),
          onSubmit = {
            if (input.isNotBlank()) {
              data.addDayTask(date.toString(), input)
              input = ""
            }
          }
        )
        TextButton(
          enabled = input.isNotBlank(),
          onClick = {
            data.addDayTask(date.toString(), input)
            input = ""
          }
        ) {
          Icon(BerillIcons.Add, contentDescription = PlannerText.addTask())
        }
      }
    }
  }
}

@Composable
internal fun TaskRow(
  task: PlannerTask,
  compact: Boolean,
  onToggle: () -> Unit,
  onNextDay: (() -> Unit)?,
  onDelete: () -> Unit
) {
  val p = parse(task.text)
  val circle by
  animateColorAsState(
    if (task.done)
      MaterialTheme.colorScheme.primary
    else
      MaterialTheme.colorScheme.surfaceContainerHighest,
    BerillMotion.effects(),
    label = "task-check"
  )
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Surface(
      onClick = onToggle,
      shape = CircleShape,
      color = circle,
      modifier = Modifier.size(24.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedVisibility(
          visible = task.done,
          enter = scaleIn(BerillMotion.fastSpatial()) + fadeIn(BerillMotion.effects()),
          exit = scaleOut(BerillMotion.effects()) + fadeOut(BerillMotion.effects())
        ) {
          Ic(BerillIcons.Check, size = 16.dp, tint = MaterialTheme.colorScheme.onPrimary)
        }
      }
    }
    if (p.time != null)
      Text(
        p.time,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.width(44.dp)
      )
    Text(
      p.title,
      modifier = Modifier.weight(1f),
      fontWeight = if (p.important) FontWeight.Bold else null,
      textDecoration = if (task.done) TextDecoration.LineThrough else null,
      color = if (task.done)
        MaterialTheme.colorScheme.onSurfaceVariant
      else
        MaterialTheme.colorScheme.onSurface,
      style = if (compact)
        MaterialTheme.typography.bodySmall
      else
        MaterialTheme.typography.bodyLarge
    )
    if (!task.done && onNextDay != null)
      IconButton(onClick = onNextDay, modifier = Modifier.size(32.dp)) {
        Icon(
          BerillIcons.Forward,
          contentDescription = PlannerText.toTomorrow(),
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
      Icon(
        BerillIcons.Close,
        contentDescription = PlannerText.delete(),
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
