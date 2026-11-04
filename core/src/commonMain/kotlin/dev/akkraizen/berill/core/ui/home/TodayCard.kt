package dev.akkraizen.berill.core.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.TodayItem
import dev.akkraizen.berill.api.TodayKind
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.connections.FailureOperation
import dev.akkraizen.berill.core.ui.CoreText

internal fun todayIcon(kind: TodayKind): ImageVector = when (kind) {
  TodayKind.TASK -> Icons.Outlined.TaskAlt
  TodayKind.DEADLINE -> Icons.Outlined.Flag
  TodayKind.EVENT -> Icons.Outlined.Event
  TodayKind.WORKOUT -> Icons.Outlined.FitnessCenter
  TodayKind.DOSE -> Icons.Outlined.Medication
  TodayKind.HABIT -> Icons.Outlined.Repeat
}

@Composable
internal fun TodayCard(core: BerillCore, editing: Boolean) {
  val date = today()
  val items by remember(date) { core.todayFeed.observe(date) }.collectAsState(emptyList())
  var expanded by rememberSaveable { mutableStateOf(false) }
  val open = items.filter { it.done != true }
  val finished = items.filter { it.done == true }
  val checkable = items.count { it.done != null }
  Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Ic(Icons.Outlined.Today, tint = MaterialTheme.colorScheme.primary)
        Text(CoreText.today(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (checkable > 0) Text(CoreText.todayProgress(finished.size, checkable)(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (checkable > 0) ProgressLine(finished.size / checkable.toFloat())
      if (items.isEmpty()) Text(CoreText.todayEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
      val visible = if (expanded || editing) open else open.take(LIMIT)
      visible.forEach { item -> SwipeableTodayRow(core, item, editing) }
      if (!expanded && open.size > LIMIT && !editing)
        TextButton(onClick = { expanded = true }) { Text(CoreText.todayMore(open.size - LIMIT)()) }
      if (finished.isNotEmpty()) {
        var showDone by rememberSaveable { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().interactive(bleed = 6.dp) { showDone = !showDone }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Ic(Icons.Outlined.CheckCircle, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(CoreText.todayDone(finished.size)(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showDone) finished.forEach { item -> SwipeableTodayRow(core, item, editing) }
      }
    }
  }
}

@Composable
private fun SwipeableTodayRow(core: BerillCore, item: TodayItem, editing: Boolean) {
  val toggle = item.toggle?.takeIf { item.done != null }
  dev.akkraizen.berill.api.ui.interaction.SwipeActions(
    start = toggle?.let {
      dev.akkraizen.berill.api.ui.interaction.RowSwipeAction(
        if (item.done == true) dev.akkraizen.berill.api.ui.UiText.swipeUndo() else dev.akkraizen.berill.api.ui.UiText.swipeDone(),
        Icons.Outlined.CheckCircle
      ) { core.connections.errors.run(FailureOperation.COMMAND, it) }
    },
    end = dev.akkraizen.berill.api.ui.interaction.RowSwipeAction(dev.akkraizen.berill.api.ui.UiText.swipeOpen(), Icons.AutoMirrored.Outlined.OpenInNew, color = MaterialTheme.colorScheme.secondary) {
      core.connections.errors.run(FailureOperation.COMMAND, item.open)
    },
    enabled = !editing
  ) { TodayRow(core, item, editing) }
}

@Composable
private fun TodayRow(core: BerillCore, item: TodayItem, editing: Boolean) {
  val colors = MaterialTheme.colorScheme
  val accent = when {
    item.overdue -> colors.error
    item.kind == TodayKind.DOSE -> colors.tertiary
    item.kind == TodayKind.EVENT -> colors.secondary
    else -> colors.primary
  }
  Row(
    Modifier.fillMaxWidth().then(if (editing) Modifier else Modifier.interactive(MaterialTheme.shapes.medium, bleed = 6.dp) { core.connections.errors.run(FailureOperation.COMMAND, item.open) }),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    val toggle = item.toggle
    if (toggle != null && item.done != null)
      Checkbox(item.done == true, { if (!editing) core.connections.errors.run(FailureOperation.COMMAND, toggle) }, Modifier.size(32.dp))
    else Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
      Box(Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Ic(todayIcon(item.kind), size = 16.dp, tint = accent)
      }
    }
    Column(Modifier.weight(1f)) {
      Text(
        item.title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (item.done == true) TextDecoration.LineThrough else null,
        color = if (item.done == true) colors.onSurfaceVariant else colors.onSurface
      )
      val detail = listOfNotNull(
        item.detail,
        item.due?.takeIf { it != today() }?.dayMonth(),
        core.plugins.enabled.value.firstOrNull { it.id == item.pluginId }?.title
      ).distinct().joinToString(" · ")
      if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (item.overdue)
      Surface(shape = MaterialTheme.shapes.small, color = colors.errorContainer) {
        Text(CoreText.todayOverdue(), style = MaterialTheme.typography.labelSmall, color = colors.onErrorContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
      }
    else if (item.time != null)
      Text(item.time!!, style = MaterialTheme.typography.labelLarge, color = accent)
    else if (toggle != null) Ic(todayIcon(item.kind), size = 16.dp, tint = colors.onSurfaceVariant)
  }
}

private const val LIMIT = 8
