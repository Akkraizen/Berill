package dev.akkraizen.berill.plugins.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import dev.akkraizen.berill.api.ui.layout.CardGrid
import dev.akkraizen.berill.api.ui.layout.ListLayout
import dev.akkraizen.berill.api.ui.layout.ListLayoutSwitcher
import dev.akkraizen.berill.api.ui.layout.rememberListLayout
import dev.akkraizen.berill.plugins.projects.data.TASKS_LAYOUT
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.KanbanBoard
import dev.akkraizen.berill.api.ui.layout.KanbanColumn
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.api.ui.selection.pastelCheckboxColors
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.projects.data.Milestone
import dev.akkraizen.berill.plugins.projects.data.Project
import dev.akkraizen.berill.plugins.projects.data.ProjectTask
import dev.akkraizen.berill.plugins.projects.data.TASK_ORDER
import dev.akkraizen.berill.plugins.projects.data.TaskPriority
import dev.akkraizen.berill.plugins.projects.data.TaskStatus
import dev.akkraizen.berill.plugins.projects.data.milestones
import dev.akkraizen.berill.plugins.projects.data.nextOrder
import dev.akkraizen.berill.plugins.projects.data.progress
import dev.akkraizen.berill.plugins.projects.data.projectTasks
import dev.akkraizen.berill.plugins.projects.i18n.ProjectsText

private enum class TaskFilter { OPEN, ALL, DONE }

@Composable
internal fun TasksCard(ctx: PluginContext, projectId: String, project: Project) {
  val collection = remember(ctx) { projectTasks(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  val steps by remember(ctx) { milestones(ctx).observe() }.collectAsState(emptyList())
  val tasks = all.filter { it.value.project == projectId }.sortedWith(TASK_ORDER)
  val own = steps.filter { it.value.project == projectId }
  val layout = rememberListLayout(ctx, TASKS_LAYOUT)
  var filter by rememberSaveable(projectId) { mutableIntStateOf(TaskFilter.OPEN.ordinal) }
  var editing by remember { mutableStateOf<String?>(null) }
  var draft by rememberDraft("projects/task/$projectId")

  fun add() {
    val title = draft.trim()
    if (title.isEmpty()) return
    collection.save(ctx.data.newId(), ProjectTask(projectId, title, order = nextOrder(tasks.map { it.value.order }), created = today().toString()))
    draft = ""
  }

  fun move(id: String, status: TaskStatus) {
    val fresh = collection.get(id) ?: return
    if (fresh.status != status) collection.save(id, fresh.moveTo(status))
  }

  SectionCard(
    ProjectsText.tasks(),
    Icons.Outlined.TaskAlt,
    trailing = { ListLayoutSwitcher(ctx, TASKS_LAYOUT) }
  ) {
    val progress = tasks.map { it.value }.progress()
    if (progress.total == 0) EmptyHint(ProjectsText.noTasks())
    else {
      ProgressLine(progress.fraction)
      Text(ProjectsText.progressOf(progress.done, progress.total)(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (layout == ListLayout.BOARD) {
      KanbanBoard(
        columns = TaskStatus.entries.map { status ->
          KanbanColumn(status.name, taskStatusLabel(status), tasks.filter { it.value.status == status }, ProjectsText.empty(), taskStatusIcon(status))
        },
        itemKey = { it.id },
        onMove = { item, key -> move(item.id, TaskStatus.valueOf(key)) },
        card = { item -> TaskBoardCard(item, own) { editing = item.id } }
      )
    } else {
      if (progress.total > 0)
        ChoiceChips(TaskFilter.entries, TaskFilter.entries[filter], { filterLabel(it) }, { filter = it.ordinal })
      val visible = when (TaskFilter.entries[filter]) {
        TaskFilter.OPEN -> tasks.filterNot { it.value.done }
        TaskFilter.ALL -> tasks
        TaskFilter.DONE -> tasks.filter { it.value.done }
      }
      if (visible.isEmpty() && progress.total > 0) EmptyHint(ProjectsText.nothingHere())
      if (layout == ListLayout.CARDS)
        CardGrid(visible, minCardWidth = 200.dp, maxColumns = 3, spacing = 10.dp) { item, modifier ->
          TaskTile(item, own, modifier, onToggle = { move(item.id, if (item.value.done) TaskStatus.TODO else TaskStatus.DONE) }) { editing = item.id }
        }
      else visible.forEach { item ->
        dev.akkraizen.berill.api.ui.interaction.SwipeActions(
          start = dev.akkraizen.berill.api.ui.interaction.RowSwipeAction(
            if (item.value.done) dev.akkraizen.berill.api.ui.UiText.swipeUndo() else dev.akkraizen.berill.api.ui.UiText.swipeDone(),
            if (item.value.done) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.CheckCircle
          ) { move(item.id, if (item.value.done) TaskStatus.TODO else TaskStatus.DONE) },
          end = dev.akkraizen.berill.api.ui.interaction.RowSwipeAction(ProjectsText.deleteTask(), BerillIcons.Delete, destructive = true) { collection.delete(item.id) }
        ) {
          TaskRow(item, own, onToggle = { move(item.id, if (item.value.done) TaskStatus.TODO else TaskStatus.DONE) }) { editing = item.id }
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      BerillTextField(
        draft,
        { draft = it },
        placeholder = { Text(ProjectsText.newTask(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        modifier = Modifier.weight(1f),
        onSubmit = { add() }
      )
      TextButton(enabled = draft.isNotBlank(), onClick = { add() }) {
        Icon(BerillIcons.Add, contentDescription = ProjectsText.addTask())
      }
    }
  }

  editing?.let { id ->
    val current = all.firstOrNull { it.id == id }?.value
    if (current != null) TaskDialog(ctx, project, current, own, onSave = { collection.save(id, it); editing = null }, onDelete = { collection.delete(id); editing = null }, onDismiss = { editing = null })
  }
}

private fun filterLabel(filter: TaskFilter): String = when (filter) {
  TaskFilter.OPEN -> ProjectsText.filterOpen()
  TaskFilter.ALL -> ProjectsText.filterAll()
  TaskFilter.DONE -> ProjectsText.filterDone()
}

@Composable
private fun TaskMeta(task: ProjectTask, steps: List<Item<Milestone>>) {
  val step = steps.firstOrNull { it.id == task.milestone }?.value
  if (task.due == null && task.priority == TaskPriority.NORMAL && step == null && task.status != TaskStatus.DOING) return
  FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    if (task.status == TaskStatus.DOING)
      Text(ProjectsText.statusDoing(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
    PriorityMark(task.priority)
    DueLabel(task.due, task.done)
    step?.let { Text(it.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
  }
}

@Composable
private fun TaskRow(item: Item<ProjectTask>, steps: List<Item<Milestone>>, onToggle: () -> Unit, onOpen: () -> Unit) {
  val task = item.value
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Checkbox(colors = pastelCheckboxColors(), checked = task.done, onCheckedChange = { onToggle() })
    Column(Modifier.weight(1f).interactive(bleed = 4.dp, onClick = onOpen), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        task.title,
        textDecoration = if (task.done) TextDecoration.LineThrough else null,
        color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      TaskMeta(task, steps)
    }
  }
}

@Composable
private fun TaskTile(item: Item<ProjectTask>, steps: List<Item<Milestone>>, modifier: Modifier, onToggle: () -> Unit, onOpen: () -> Unit) {
  val task = item.value
  val accent = when {
    task.done -> MaterialTheme.colorScheme.primary
    task.overdue() -> MaterialTheme.colorScheme.error
    task.priority == TaskPriority.HIGH -> MaterialTheme.colorScheme.error
    task.status == TaskStatus.DOING -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.outlineVariant
  }
  Surface(modifier.interactive(MaterialTheme.shapes.large, onClick = onOpen), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Row(Modifier.height(IntrinsicSize.Min)) {
      Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
      Column(Modifier.weight(1f).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Checkbox(colors = pastelCheckboxColors(), checked = task.done, onCheckedChange = { onToggle() })
          Ic(taskStatusIcon(task.status), size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(taskStatusLabel(task.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(
          task.title,
          style = MaterialTheme.typography.titleSmall,
          textDecoration = if (task.done) TextDecoration.LineThrough else null,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 12.dp)
        )
        Box(Modifier.padding(start = 12.dp)) { TaskMeta(task.copy(status = if (task.status == TaskStatus.DOING) TaskStatus.TODO else task.status), steps) }
      }
    }
  }
}

@Composable
private fun TaskBoardCard(item: Item<ProjectTask>, steps: List<Item<Milestone>>, onOpen: () -> Unit) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(item.value.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.interactive(bleed = 6.dp, onClick = onOpen))
    TaskMeta(item.value.copy(status = if (item.value.status == TaskStatus.DOING) TaskStatus.TODO else item.value.status), steps)
  }
}

@Composable
private fun TaskDialog(
  ctx: PluginContext,
  project: Project,
  initial: ProjectTask,
  steps: List<Item<Milestone>>,
  onSave: (ProjectTask) -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit
) {
  var title by remember { mutableStateOf(initial.title) }
  var status by remember { mutableStateOf(initial.status) }
  var priority by remember { mutableStateOf(initial.priority) }
  var due by remember { mutableStateOf(initial.due) }
  var milestone by remember { mutableStateOf(initial.milestone) }
  var note by remember { mutableStateOf(initial.note) }
  var details by remember { mutableStateOf(initial.details.orEmpty()) }
  var confirmDelete by remember { mutableStateOf(false) }
  val noteOptions = (project.notes + listOfNotNull(initial.note)).distinct()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(ProjectsText.editTask()) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BerillTextField(title, { title = it.take(200) }, label = { Text(ProjectsText.name()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ChoiceChips(TaskStatus.entries, status, ::taskStatusLabel, { status = it }, icon = ::taskStatusIcon)
        Text(ProjectsText.priority(), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(TaskPriority.entries, priority, ::priorityLabel, { priority = it })
        DateField(due, { due = it }, ProjectsText.due())
        if (steps.isNotEmpty())
          DropdownField(ProjectsText.milestone(), listOf<String?>(null) + steps.map { it.id }, milestone, { id -> steps.firstOrNull { it.id == id }?.value?.title ?: ProjectsText.noMilestone() }, { milestone = it })
        if (noteOptions.isNotEmpty())
          DropdownField(ProjectsText.linkedNote(), listOf<String?>(null) + noteOptions, note, { id -> id?.let { ctx.notes.get(it)?.title } ?: ProjectsText.noNote() }, { note = it })
        BerillTextField(details, { details = it.take(4000) }, label = { Text(ProjectsText.details()) }, modifier = Modifier.fillMaxWidth())
        note?.let { id ->
          TextButton(onClick = { ctx.nav.openNote(id); onDismiss() }) { Text(ProjectsText.openNote()) }
        }
        TextButton(onClick = { confirmDelete = true }) {
          Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.error)
          Text(ProjectsText.deleteTask(), color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = title.isNotBlank() && (due == null || due.toDateOrNull() != null), onClick = {
        val next = initial.copy(title = title.trim(), priority = priority, due = due, milestone = milestone, note = note, details = details.trim().ifEmpty { null })
        onSave(if (next.status != status) next.moveTo(status) else next)
      }) { Text(UiText.save()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.cancel()) } }
  )
  if (confirmDelete)
    ConfirmDialog(ProjectsText.deleteTask(), initial.title, onConfirm = onDelete, onDismiss = { confirmDelete = false })
}
