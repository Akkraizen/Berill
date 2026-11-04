package dev.akkraizen.berill.plugins.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Timeline
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.CardGrid
import dev.akkraizen.berill.api.ui.selection.pastelCheckboxColors
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.projects.data.MILESTONE_ORDER
import dev.akkraizen.berill.plugins.projects.data.Milestone
import dev.akkraizen.berill.plugins.projects.data.MilestoneStyle
import dev.akkraizen.berill.plugins.projects.data.Progress
import dev.akkraizen.berill.plugins.projects.data.deleteMilestone
import dev.akkraizen.berill.plugins.projects.data.milestones
import dev.akkraizen.berill.plugins.projects.data.nextOrder
import dev.akkraizen.berill.plugins.projects.data.progress
import dev.akkraizen.berill.plugins.projects.data.projectTasks
import dev.akkraizen.berill.plugins.projects.data.rememberMilestoneStyle
import dev.akkraizen.berill.plugins.projects.data.setMilestoneStyle
import dev.akkraizen.berill.plugins.projects.i18n.ProjectsText

internal fun milestoneStyleLabel(style: MilestoneStyle): String = when (style) {
  MilestoneStyle.TIMELINE -> ProjectsText.styleTimeline()
  MilestoneStyle.ROADMAP -> ProjectsText.styleRoadmap()
  MilestoneStyle.CARDS -> ProjectsText.styleCards()
}

internal fun milestoneStyleIcon(style: MilestoneStyle): ImageVector = when (style) {
  MilestoneStyle.TIMELINE -> Icons.Outlined.Timeline
  MilestoneStyle.ROADMAP -> Icons.Outlined.CalendarViewWeek
  MilestoneStyle.CARDS -> Icons.Outlined.GridView
}

private class Step(val item: Item<Milestone>, val progress: Progress) {
  val value: Milestone get() = item.value
  val overdue: Boolean get() = !value.done && (value.due.toDateOrNull()?.let { it < today() } ?: false)
}

@Composable
internal fun MilestonesCard(ctx: PluginContext, projectId: String) {
  val collection = remember(ctx) { milestones(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  val tasks by remember(ctx) { projectTasks(ctx).observe() }.collectAsState(emptyList())
  val style = rememberMilestoneStyle(ctx)
  val steps = all.filter { it.value.project == projectId }.sortedWith(MILESTONE_ORDER).map { item ->
    Step(item, tasks.filter { it.value.milestone == item.id }.map { it.value }.progress())
  }
  var editing by remember { mutableStateOf<String?>(null) }
  var draft by rememberDraft("projects/milestone/$projectId")

  fun add() {
    val title = draft.trim()
    if (title.isEmpty()) return
    collection.save(ctx.data.newId(), Milestone(projectId, title, order = nextOrder(steps.map { it.value.order })))
    draft = ""
  }

  fun toggle(id: String, value: Boolean) {
    collection.get(id)?.let { collection.save(id, it.withDone(value)) }
  }

  SectionCard(
    ProjectsText.milestones(),
    Icons.Outlined.Flag,
    collapseKey = "projects/$projectId/milestones",
    trailing = { MilestoneStyleSwitcher(style) { setMilestoneStyle(ctx, it) } }
  ) {
    if (steps.isEmpty()) EmptyHint(ProjectsText.noMilestones())
    else when (style) {
      MilestoneStyle.TIMELINE -> Timeline(steps, ::toggle) { editing = it }
      MilestoneStyle.ROADMAP -> Roadmap(steps) { editing = it }
      MilestoneStyle.CARDS -> CardGrid(steps, minCardWidth = 180.dp, maxColumns = 4) { step, modifier ->
        MilestoneTile(step, modifier, ::toggle) { editing = step.item.id }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      BerillTextField(
        draft,
        { draft = it },
        placeholder = { Text(ProjectsText.newMilestone(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        modifier = Modifier.weight(1f),
        onSubmit = { add() }
      )
      TextButton(enabled = draft.isNotBlank(), onClick = { add() }) {
        Icon(BerillIcons.Add, contentDescription = ProjectsText.newMilestone())
      }
    }
  }

  editing?.let { id ->
    all.firstOrNull { it.id == id }?.value?.let { current ->
      MilestoneDialog(
        current,
        onSave = { collection.save(id, it); editing = null },
        onDelete = { deleteMilestone(ctx, id); editing = null },
        onDismiss = { editing = null }
      )
    }
  }
}

@Composable
private fun MilestoneStyleSwitcher(current: MilestoneStyle, onSelect: (MilestoneStyle) -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }) { Icon(milestoneStyleIcon(current), ProjectsText.milestoneStyle()) }
    DropdownMenu(open, onDismissRequest = { open = false }) {
      MilestoneStyle.entries.forEach { style ->
        DropdownMenuItem(
          text = { Text(milestoneStyleLabel(style)) },
          leadingIcon = { Ic(milestoneStyleIcon(style), size = 20.dp) },
          trailingIcon = if (style == current) ({ Ic(BerillIcons.Check, size = 18.dp) }) else null,
          onClick = { onSelect(style); open = false }
        )
      }
    }
  }
}

@Composable
private fun stepColor(step: Step): Color = when {
  step.value.done -> MaterialTheme.colorScheme.primary
  step.overdue -> MaterialTheme.colorScheme.error
  step.progress.done > 0 -> MaterialTheme.colorScheme.tertiary
  else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun StepNode(step: Step, size: Dp = 28.dp, onClick: (() -> Unit)? = null) {
  val color = stepColor(step)
  val modifier = if (onClick != null) Modifier.size(size).interactive(CircleShape, onClick = onClick) else Modifier.size(size)
  Box(modifier, contentAlignment = Alignment.Center) {
    if (step.value.done) {
      Surface(Modifier.size(size), shape = CircleShape, color = color) {
        Box(contentAlignment = Alignment.Center) { Ic(BerillIcons.Check, size = size * 0.6f, tint = MaterialTheme.colorScheme.onPrimary) }
      }
    } else {
      CircularProgressIndicator(
        progress = { step.progress.fraction },
        modifier = Modifier.size(size),
        color = color,
        trackColor = color.copy(alpha = 0.22f),
        strokeWidth = 3.dp,
        gapSize = 0.dp
      )
      Box(Modifier.size(size * 0.32f).background(color, CircleShape))
    }
  }
}

@Composable
private fun StepText(step: Step, align: TextAlign = TextAlign.Start, maxLines: Int = 2) {
  Text(
    step.value.title,
    style = MaterialTheme.typography.bodyLarge,
    textAlign = align,
    textDecoration = if (step.value.done) TextDecoration.LineThrough else null,
    color = if (step.value.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis
  )
}

@Composable
private fun StepFacts(step: Step) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    DueLabel(step.value.due, step.value.done)
    if (step.progress.total > 0)
      Text(ProjectsText.progressOf(step.progress.done, step.progress.total)(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun Timeline(steps: List<Step>, onToggle: (String, Boolean) -> Unit, onOpen: (String) -> Unit) {
  Column {
    steps.forEachIndexed { index, step ->
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
          Box(Modifier.width(2.dp).height(6.dp).background(if (index == 0) Color.Transparent else MaterialTheme.colorScheme.outlineVariant))
          StepNode(step) { onToggle(step.item.id, !step.value.done) }
          Box(Modifier.width(2.dp).weight(1f).background(if (index == steps.lastIndex) Color.Transparent else if (step.value.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
        }
        Column(Modifier.weight(1f).padding(top = 6.dp, bottom = 14.dp).interactive(bleed = 4.dp) { onOpen(step.item.id) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
          StepText(step)
          StepFacts(step)
        }
      }
    }
  }
}

@Composable
private fun Roadmap(steps: List<Step>, onOpen: (String) -> Unit) {
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
    steps.forEachIndexed { index, step ->
      Column(Modifier.width(150.dp).interactive(bleed = 0.dp) { onOpen(step.item.id) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(3.dp).background(if (index == 0) Color.Transparent else lineColor(steps[index - 1])))
            Box(Modifier.width(32.dp))
            Box(Modifier.weight(1f).height(3.dp).background(if (index == steps.lastIndex) Color.Transparent else lineColor(step)))
          }
          StepNode(step, 32.dp)
        }
        Column(Modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
          StepText(step, TextAlign.Center)
          DueLabel(step.value.due, step.value.done)
          if (step.progress.total > 0)
            Text(ProjectsText.progressOf(step.progress.done, step.progress.total)(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
private fun lineColor(step: Step): Color = if (step.value.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

@Composable
private fun MilestoneTile(step: Step, modifier: Modifier, onToggle: (String, Boolean) -> Unit, onOpen: () -> Unit) {
  val color = stepColor(step)
  Surface(modifier.interactive(MaterialTheme.shapes.large, onClick = onOpen), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(contentAlignment = Alignment.Center) {
          CircularProgressIndicator(
            progress = { if (step.value.done) 1f else step.progress.fraction },
            modifier = Modifier.size(48.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            strokeWidth = 5.dp,
            gapSize = 0.dp
          )
          if (step.value.done) Ic(BerillIcons.Check, size = 22.dp, tint = color)
          else Text("${(step.progress.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = color)
        }
        Checkbox(colors = pastelCheckboxColors(), checked = step.value.done, onCheckedChange = { onToggle(step.item.id, it) })
      }
      StepText(step)
      StepFacts(step)
    }
  }
}

@Composable
private fun MilestoneDialog(initial: Milestone, onSave: (Milestone) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
  var title by remember { mutableStateOf(initial.title) }
  var due by remember { mutableStateOf(initial.due) }
  var confirmDelete by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(ProjectsText.milestone()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BerillTextField(title, { title = it.take(200) }, label = { Text(ProjectsText.name()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        DateField(due, { due = it }, ProjectsText.due())
        Text(ProjectsText.milestoneHint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { confirmDelete = true }) {
          Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.error)
          Text(UiText.delete(), color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = title.isNotBlank() && (due == null || due.toDateOrNull() != null), onClick = {
        onSave(initial.copy(title = title.trim(), due = due))
      }) { Text(UiText.save()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.cancel()) } }
  )
  if (confirmDelete) ConfirmDialog(ProjectsText.milestone(), initial.title, onConfirm = onDelete, onDismiss = { confirmDelete = false })
}
