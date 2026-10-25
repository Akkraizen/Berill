package dev.akkraizen.berill.plugins.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.deadlineInfo
import dev.akkraizen.berill.api.ui.dialogs.DeleteButton
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.fields.BodyEditor
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.api.ui.toDateOrNull

@Composable
internal fun TaskScreen(ctx: PluginContext, id: String) {
  val collection = remember(ctx) { tasks(ctx) }
  val subjectNames by remember(ctx) { subjects(ctx).observe() }.collectAsState(emptyList())
  val task by remember(collection, id) { collection.observe(id) }.collectAsState(collection.get(id))
  val t = task
    ?: run {
      EmptyHint(StudyText.taskDeleted())
      return
    }

  fun update(transform: (StudyTask) -> StudyTask) {
    val current = collection.get(id) ?: return
    collection.save(id, transform(current))
  }

  val info = deadlineInfo(t.deadline.toDateOrNull())

  PluginList {
    item {
      DetailHeader(t.shortTitle.ifBlank { StudyText.untitled() }, statusIcon(t.normalizedStatus)) {
        DeleteButton(StudyText.taskNoun()) {
          collection.delete(id)
          ctx.nav.back()
        }
      }
    }
    item {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        ChoiceChips(
          TASK_STATUSES,
          t.normalizedStatus,
          { StudyText.statusLabel(it) },
          { s -> update { it.copy(status = s) } },
          Modifier.weight(1f),
          icon = ::statusIcon
        )
        if (t.normalizedStatus != SUBMITTED)
          Text(info.label, color = info.tone.color(), style = MaterialTheme.typography.titleSmall)
      }
    }
    item {
      TwoPane(
        left = {
          SectionCard(StudyText.taskAndNotes()) {
            BodyEditor(
              t.body ?: "",
              { v -> update { it.copy(body = v.ifBlank { null }) } },
              StudyText.taskBodyPlaceholder()
            )
          }
        },
        right = {
          SectionCard(StudyText.properties()) {
            CommitTextField(
              t.title,
              { v -> update { it.copy(title = v.trim()) } },
              StudyText.name()
            )
            DropdownField(
              StudyText.subject(),
              listOf(NO_SUBJECT) + subjectNames.map { it.value.title }.sorted(),
              t.subject ?: NO_SUBJECT,
              { it },
              { s -> update { it.copy(subject = s.takeIf { v -> v != NO_SUBJECT }) } },
              Modifier.fillMaxWidth()
            )
            DropdownField(
              StudyText.type(),
              rememberTaskTypes(ctx),
              t.type,
              { it },
              { v -> update { it.copy(type = v) } },
              Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              DateField(
                t.deadline,
                { v -> update { it.copy(deadline = v) } },
                StudyText.deadline(),
                Modifier.weight(1f)
              )
              CommitTextField(
                t.grade ?: "",
                { v -> update { it.copy(grade = v.trim().ifEmpty { null }) } },
                StudyText.gradeLabel(),
                Modifier.weight(1f)
              )
            }
          }
        }
      )
    }
  }
}
