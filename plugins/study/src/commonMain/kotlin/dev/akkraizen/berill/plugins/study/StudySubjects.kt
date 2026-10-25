package dev.akkraizen.berill.plugins.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.DeleteButton
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.fields.BodyEditor
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.fields.IntField
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.selection.ChoiceChips

@Composable
internal fun SubjectsCard(
  subjects: List<Item<Subject>>,
  tasks: List<Item<StudyTask>>,
  filter: String?,
  onToggleFilter: (String) -> Unit,
  onOpen: (Item<Subject>) -> Unit,
  onAdd: () -> Unit
) {
  val zet = subjects.sumOf { it.value.zet ?: 0 }
  val exams = subjects.count { it.value.isExam }
  val credits = subjects.count { it.value.isCredit && !it.value.isExam }
  SectionCard(
    StudyText.subjects(),
    Icons.Outlined.Class,
    trailing = {
      TextButton(onClick = onAdd) {
        Ic(BerillIcons.Add, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(StudyText.subject())
      }
    }
  ) {
    Text(
      StudyText.subjectsSummary(subjects.size, zet, exams, credits)(),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (subjects.isEmpty()) EmptyHint(StudyText.noSubjects())
    subjects.forEach { item ->
      val s = item.value
      val own = tasks.filter { it.value.subject == s.title }
      val open = own.count { it.value.normalizedStatus != SUBMITTED }
      val closed = own.size - open
      val selected = filter == s.title
      Surface(
        onClick = { onToggleFilter(s.title) },
        shape = MaterialTheme.shapes.large,
        color = if (selected)
          MaterialTheme.colorScheme.secondaryContainer
        else
          MaterialTheme.colorScheme.surfaceContainer,
        border = if (s.elective) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              s.title,
              style = MaterialTheme.typography.titleSmall,
              modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onOpen(item) }) { Text(StudyText.open()) }
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            s.zet?.let { Badge(StudyText.zetBadge(it)()) }
            if (s.isExam)
              Badge(StudyText.exam())
            else if (s.isCredit)
              Badge(StudyText.credit())
            if (s.elective) Badge(StudyText.elective())
            listOf(
              StudyText.lecturesShort() to s.lectures,
              StudyText.practiceShort() to s.practice,
              StudyText.labsShort() to s.labs,
              StudyText.selfStudyShort() to s.selfStudy
            )
              .filter { (_, h) -> (h ?: 0) > 0 }
              .forEach { (label, h) -> Badge("$label $h", muted = true) }
          }
          Text(
            if (own.isEmpty())
              StudyText.noTasksLower()
            else
              StudyText.openClosed(open, closed)(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
internal fun NewSubjectDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
  var name by remember { mutableStateOf("") }
  val confirm: () -> Unit = { onAdd(name.trim()) }
  DialogKeys(onConfirm = confirm, confirmEnabled = name.isNotBlank(), onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(StudyText.newSubject()) },
    text = {
      BerillTextField(
        name,
        { name = it },
        label = { Text(StudyText.subjectName()) },
        singleLine = true
      )
    },
    confirmButton = {
      TextButton(enabled = name.isNotBlank(), onClick = confirm) { Text(StudyText.create()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(StudyText.cancel()) } }
  )
}

@Composable
internal fun SubjectScreen(ctx: PluginContext, id: String) {
  val collection = remember(ctx) { subjects(ctx) }
  val allTasks by remember(ctx) { tasks(ctx).observe() }.collectAsState(emptyList())
  val subject by
  remember(collection, id) { collection.observe(id) }.collectAsState(collection.get(id))
  val s = subject
    ?: run {
      EmptyHint(StudyText.subjectDeleted())
      return
    }

  fun update(transform: (Subject) -> Subject) {
    val current = collection.get(id) ?: return
    collection.save(id, transform(current))
  }

  val own = activeSorted(allTasks.filter { it.value.subject == s.title }) +
    allTasks.filter { it.value.subject == s.title && it.value.normalizedStatus == SUBMITTED }

  PluginList {
    item {
      DetailHeader(s.title.ifBlank { StudyText.untitled() }, Icons.Outlined.Class) {
        DeleteButton(StudyText.subjectNoun()) {
          collection.delete(id)
          ctx.nav.back()
        }
      }
    }
    item {
      TwoPane(
        left = {
          SectionCard(StudyText.tasks()) {
            if (own.isEmpty()) EmptyHint(StudyText.noTasks())
            own.forEach { item ->
              TaskRow(
                item,
                onCycle = {
                  tasks(ctx).let { c ->
                    c.get(item.id)?.let { t ->
                      c.save(item.id, t.copy(status = nextStatus(t.normalizedStatus)))
                    }
                  }
                },
                onOpen = { ctx.nav.open(StudyPlugin.TASK_ROUTE + item.id) }
              )
            }
          }
          SectionCard(StudyText.notes()) {
            BodyEditor(
              s.body ?: "",
              { v -> update { it.copy(body = v.ifBlank { null }) } },
              StudyText.subjectNotesPlaceholder()
            )
          }
        },
        right = {
          SectionCard(StudyText.properties()) {
            CommitTextField(
              s.title,
              { v ->
                val oldName = collection.get(id)?.title
                val newName = v.trim()
                if (oldName != null && oldName != newName) {
                  update { it.copy(title = newName) }

                  val taskCollection = tasks(ctx)
                  taskCollection
                    .all()
                    .filter { it.value.subject == oldName }
                    .forEach { t ->
                      taskCollection.save(t.id, t.value.copy(subject = newName))
                    }
                }
              },
              StudyText.name()
            )
            ChoiceChips(
              listOf("экзамен", "зачёт"),
              if (s.isExam) "экзамен" else "зачёт",
              { StudyText.controlLabel(it) },
              { c -> update { it.copy(control = c) } }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(StudyText.electiveSubject(), modifier = Modifier.weight(1f))
              Switch(
                checked = s.elective,
                onCheckedChange = { v -> update { it.copy(elective = v) } })
            }
            CommitTextField(
              s.teacher ?: "",
              { v -> update { it.copy(teacher = v.trim().ifEmpty { null }) } },
              StudyText.teacher()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              IntField(
                s.zet,
                { v -> update { it.copy(zet = v) } },
                StudyText.zet(),
                Modifier.weight(1f)
              )
              CommitTextField(
                s.semester ?: "",
                { v -> update { it.copy(semester = v.trim().ifEmpty { null }) } },
                StudyText.semester(),
                Modifier.weight(1f)
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              IntField(
                s.lectures,
                { v -> update { it.copy(lectures = v) } },
                StudyText.lecturesHours(),
                Modifier.weight(1f)
              )
              IntField(
                s.practice,
                { v -> update { it.copy(practice = v) } },
                StudyText.practiceHours(),
                Modifier.weight(1f)
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              IntField(
                s.labs,
                { v -> update { it.copy(labs = v) } },
                StudyText.labsHours(),
                Modifier.weight(1f)
              )
              IntField(
                s.selfStudy,
                { v -> update { it.copy(selfStudy = v) } },
                StudyText.selfStudyHours(),
                Modifier.weight(1f)
              )
            }
          }
        }
      )
    }
  }
}
