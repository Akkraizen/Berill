package dev.akkraizen.berill.plugins.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.projects.data.TaskPriority
import dev.akkraizen.berill.plugins.projects.data.TaskStatus
import dev.akkraizen.berill.plugins.projects.i18n.ProjectsText

internal fun taskStatusLabel(status: TaskStatus): String = when (status) {
  TaskStatus.TODO -> ProjectsText.statusTodo()
  TaskStatus.DOING -> ProjectsText.statusDoing()
  TaskStatus.DONE -> ProjectsText.statusDone()
}

internal fun taskStatusIcon(status: TaskStatus): ImageVector = when (status) {
  TaskStatus.TODO -> Icons.Outlined.RadioButtonUnchecked
  TaskStatus.DOING -> Icons.Outlined.PlayCircle
  TaskStatus.DONE -> Icons.Outlined.CheckCircle
}

internal fun priorityLabel(priority: TaskPriority): String = when (priority) {
  TaskPriority.LOW -> ProjectsText.priorityLow()
  TaskPriority.NORMAL -> ProjectsText.priorityNormal()
  TaskPriority.HIGH -> ProjectsText.priorityHigh()
}

internal fun dateText(value: String?): String? {
  val date = value.toDateOrNull() ?: return null
  return when (date) {
    today() -> ProjectsText.today()
    else -> if (date.year == today().year) date.dayMonth() else "${date.dayMonth()} ${date.year}"
  }
}

@Composable
internal fun DueLabel(value: String?, done: Boolean = false) {
  val date = value.toDateOrNull() ?: return
  val overdue = !done && date < today()
  val color = when {
    done -> MaterialTheme.colorScheme.onSurfaceVariant
    overdue -> MaterialTheme.colorScheme.error
    date == today() -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Ic(Icons.Outlined.Event, size = 14.dp, tint = color)
    Text(
      if (overdue) ProjectsText.overdue(dateText(value).orEmpty())() else dateText(value).orEmpty(),
      style = MaterialTheme.typography.labelMedium,
      color = color,
      maxLines = 1
    )
  }
}

@Composable
internal fun PriorityMark(priority: TaskPriority) {
  if (priority == TaskPriority.NORMAL) return
  val color = if (priority == TaskPriority.HIGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    Ic(Icons.Outlined.Flag, size = 14.dp, tint = color)
    Text(priorityLabel(priority), style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
  }
}

@Composable
internal fun TagChips(tags: List<String>, color: Color? = null) {
  if (tags.isEmpty()) return
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    tags.forEach { tag ->
      BerillChip(
        onClick = null,
        label = {
          Text("#$tag", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = color ?: MaterialTheme.colorScheme.primary)
        }
      )
    }
  }
}
