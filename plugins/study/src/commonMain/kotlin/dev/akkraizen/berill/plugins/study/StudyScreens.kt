package dev.akkraizen.berill.plugins.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.deadlineInfo
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today

internal val NO_SUBJECT: String
  get() = StudyText.noSubject()

@Composable
internal fun StudyScreen(ctx: PluginContext) {
  val taskCollection = remember(ctx) { tasks(ctx) }
  val subjectCollection = remember(ctx) { subjects(ctx) }
  val allTasks by remember(taskCollection) { taskCollection.observe() }.collectAsState(emptyList())
  val allSubjects by
  remember(subjectCollection) { subjectCollection.observe() }.collectAsState(emptyList())
  var filter by rememberSaveable { mutableStateOf<String?>(null) }
  val taskTypes = rememberTaskTypes(ctx)
  var showDone by rememberSaveable { mutableStateOf(false) }
  var addingSubject by remember { mutableStateOf(false) }

  val subjectsSorted = allSubjects.sortedWith(
    compareByDescending<Item<Subject>> { it.value.isExam }
      .thenBy { it.value.title.lowercase() }
  )
  val shownTasks = if (filter == null) allTasks else allTasks.filter { it.value.subject == filter }
  val active = activeSorted(shownTasks)
  val done = shownTasks
    .filter { it.value.normalizedStatus == SUBMITTED }
    .sortedByDescending { it.value.deadline ?: "" }

  fun cycle(item: Item<StudyTask>) {
    val current = taskCollection.get(item.id) ?: return
    taskCollection.save(item.id, current.copy(status = nextStatus(current.normalizedStatus)))
  }

  PluginList {
    item {
      TwoPane(
        left = {
          SectionCard(
            StudyText.deadlines() + (filter?.let { " · $it" } ?: ""),
            Icons.Outlined.Alarm,
            trailing = if (filter != null) {
              {
                TextButton(onClick = { filter = null }) {
                  Ic(BerillIcons.Close, size = 18.dp)
                  Spacer(Modifier.width(6.dp))
                  Text(StudyText.allSubjects())
                }
              }
            } else
              null
          ) {
            if (active.isEmpty()) EmptyHint(StudyText.noActiveTasks())
            active.forEach { item ->
              TaskRow(
                item,
                onCycle = { cycle(item) },
                onOpen = { ctx.nav.open(StudyPlugin.TASK_ROUTE + item.id) })
            }
            if (done.isNotEmpty()) {
              TextButton(onClick = { showDone = !showDone }) {
                Ic(
                  if (showDone) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                  size = 18.dp
                )
                Spacer(Modifier.width(6.dp))
                Text(StudyText.submittedCount(done.size)())
              }
              if (showDone)
                done.forEach { item ->
                  TaskRow(
                    item,
                    onCycle = { cycle(item) },
                    onOpen = { ctx.nav.open(StudyPlugin.TASK_ROUTE + item.id) })
                }
            }
          }
        },
        right = {
          SectionCard(StudyText.newTask(), Icons.Outlined.AddTask) {
            NewTaskForm(subjectsSorted.map { it.value.title }, taskTypes, filter) { task ->
              taskCollection.save(ctx.data.newId(), task)
            }
          }
          SubjectsCard(
            subjects = subjectsSorted,
            tasks = allTasks,
            filter = filter,
            onToggleFilter = { name -> filter = if (filter == name) null else name },
            onOpen = { item -> ctx.nav.open(StudyPlugin.SUBJECT_ROUTE + item.id) },
            onAdd = { addingSubject = true }
          )
        }
      )
    }
    item { StudyStatistics(shownTasks.map { it.value }) }
  }

  if (addingSubject)
    NewSubjectDialog(
      onDismiss = { addingSubject = false },
      onAdd = { name ->
        val id = ctx.data.newId()
        subjectCollection.save(id, Subject(title = name, control = "зачёт"))
        addingSubject = false
        ctx.nav.open(StudyPlugin.SUBJECT_ROUTE + id)
      }
    )
}

@Composable
internal fun TaskRow(item: Item<StudyTask>, onCycle: () -> Unit, onOpen: () -> Unit) {
  val t = item.value
  val submitted = t.normalizedStatus == SUBMITTED
  Row(
    Modifier.fillMaxWidth().alpha(if (submitted) 0.6f else 1f),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(onClick = onCycle) {
      Icon(
        statusIcon(t.normalizedStatus),
        contentDescription = StudyText.statusDescription(t.normalizedStatus)(),
        tint = if (submitted)
          MaterialTheme.colorScheme.primary
        else
          MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Column(Modifier.weight(1f).interactive(bleed = 6.dp, onClick = onOpen)) {
      Text(
        t.shortTitle,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      val sub =
        listOfNotNull(
          t.subject,
          t.type,
          t.normalizedStatus.takeIf { it != TODO }?.let { StudyText.statusLabel(it) })
          .joinToString(" · ")
      if (sub.isNotEmpty())
        Text(
          sub,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (submitted) {
      Text(
        t.grade?.let { StudyText.grade(it)() } ?: StudyText.submittedShort(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
      )
    } else {
      val info = deadlineInfo(t.deadline.toDateOrNull())
      Text(info.label, style = MaterialTheme.typography.labelLarge, color = info.tone.color())
    }
  }
}

@Composable
private fun NewTaskForm(
  subjects: List<String>,
  types: List<String>,
  preselected: String?,
  onAdd: (StudyTask) -> Unit
) {
  var title by rememberDraft("study/new-task/title")
  var subject by
  rememberSaveable(preselected, subjects.isEmpty()) {
    mutableStateOf(preselected ?: subjects.firstOrNull())
  }
  var type by rememberSaveable { mutableStateOf(types.firstOrNull() ?: "Другое") }
  if (type !in types) type = types.firstOrNull() ?: "Другое"
  var deadline by rememberDraft("study/new-task/deadline", today().plusDays(7).toString())
  var error by remember { mutableStateOf<String?>(null) }
  fun submit() {
    val name = title.trim()
    if (name.isEmpty()) {
      error = StudyText.enterTaskName()
      return
    }
    onAdd(
      StudyTask(
        title = if (subject != null) "$subject — $name" else name,
        subject = subject,
        type = type,
        deadline = deadline.toDateOrNull()?.toString()
      )
    )
    title = ""
    deadline = today().plusDays(7).toString()
  }

  BerillTextField(
    title,
    {
      title = it
      error = null
    },
    label = { Text(StudyText.whatToDo()) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
    onSubmit = { submit() }
  )
  DropdownField(
    StudyText.subject(),
    listOf(NO_SUBJECT) + subjects,
    subject ?: NO_SUBJECT,
    { it },
    { subject = it.takeIf { s -> s != NO_SUBJECT } },
    Modifier.fillMaxWidth()
  )
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    DropdownField(StudyText.type(), types, type, { it }, { type = it }, Modifier.weight(1f))
    BerillTextField(
      deadline,
      { deadline = it },
      label = { Text(StudyText.deadline()) },
      singleLine = true,
      isError = deadline.isNotBlank() && deadline.toDateOrNull() == null,
      modifier = Modifier.weight(1f),
      onSubmit = { submit() }
    )
  }
  error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  Button(onClick = { submit() }) { Text(StudyText.add()) }
}

@Composable
internal fun Badge(text: String, muted: Boolean = false) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = if (muted)
      MaterialTheme.colorScheme.surfaceContainerHighest
    else
      MaterialTheme.colorScheme.primaryContainer
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
  }
}
