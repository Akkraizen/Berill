package dev.akkraizen.berill.plugins.study

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.Capability
import dev.akkraizen.berill.api.Deadline
import dev.akkraizen.berill.api.DeadlineSource
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.observeStringList
import dev.akkraizen.berill.api.setStringList
import dev.akkraizen.berill.api.stringList
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.deadlineInfo
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.fields.EditableList
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.toDateOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object StudyPlugin : BerillPlugin {
  const val SUBJECTS = "subjects"
  const val TASKS = "study_tasks"

  override val id = "study"
  override val title
    get() = StudyText.title()

  override val icon = Icons.Outlined.School
  override val order = 60
  override val description
    get() = StudyText.description()

  override val installedByDefault = true
  override val hasSettings = true

  @Composable
  override fun Settings(ctx: PluginContext) {
    val types = rememberTaskTypes(ctx)
    SectionCard(StudyText.taskTypes(), Icons.Outlined.Category) {
      EditableList(
        items = types,
        onChange = { ctx.settings.setStringList(TASK_TYPES_KEY, it) },
        placeholder = StudyText.newType(),
        onReset = { ctx.settings.setStringList(TASK_TYPES_KEY, TASK_TYPES) },
        draftKey = "study/task-types"
      )
    }
  }

  override val collections = listOf(SUBJECTS, TASKS)

  internal const val TASK_ROUTE = "task:"
  internal const val SUBJECT_ROUTE = "subject:"

  @Composable
  override fun Screen(ctx: PluginContext) {
    val route by ctx.nav.route.collectAsState()
    val r = route

    AnimatedRoute(r) { r ->
      when {
        r != null && r.startsWith(TASK_ROUTE) -> TaskScreen(ctx, r.removePrefix(TASK_ROUTE))
        r != null && r.startsWith(SUBJECT_ROUTE) ->
          SubjectScreen(ctx, r.removePrefix(SUBJECT_ROUTE))

        else -> StudyScreen(ctx)
      }
    }
  }

  override fun homeCards(ctx: PluginContext) =
    listOf(HomeCard("deadlines", StudyText.deadlines, order = 20) { NearestDeadlines(ctx) })

  override fun provides(ctx: PluginContext): List<Capability> =
    listOf(
      object : DeadlineSource {
        override fun deadlines(): Flow<List<Deadline>> =
          tasks(ctx).observe().map { list ->
            list
              .filter { it.value.normalizedStatus != SUBMITTED }
              .mapNotNull { item ->
                val date = item.value.deadline.toDateOrNull() ?: return@mapNotNull null
                Deadline(item.value.fullTitle, date, id, TASK_ROUTE + item.id)
              }
          }
      }
    )
}

const val TODO = "не начато"
const val DOING = "в работе"
const val SUBMITTED = "сдано"
val TASK_STATUSES = listOf(TODO, DOING, SUBMITTED)

fun statusIcon(status: String): ImageVector =
  when (status) {
    DOING -> Icons.Outlined.Pending
    SUBMITTED -> Icons.Outlined.CheckCircle
    else -> Icons.Outlined.RadioButtonUnchecked
  }

fun nextStatus(status: String) = TASK_STATUSES[(TASK_STATUSES.indexOf(status).coerceAtLeast(0) + 1) % TASK_STATUSES.size]

internal const val TASK_TYPES_KEY = "taskTypes"

@Composable
internal fun rememberTaskTypes(ctx: PluginContext): List<String> {
  val custom by
    remember(ctx) { ctx.settings.observeStringList(TASK_TYPES_KEY) }
      .collectAsState(ctx.settings.stringList(TASK_TYPES_KEY))
  return custom?.takeIf { it.isNotEmpty() } ?: TASK_TYPES
}

val TASK_TYPES = listOf(
  "Лабораторная",
  "Практическая",
  "Курсовая",
  "Реферат",
  "Доклад",
  "Контрольная",
  "Зачёт",
  "Экзамен",
  "Проект",
  "Другое"
)

@Serializable
data class Subject(
  val title: String = "",
  val zet: Int? = null,
  val control: String? = null,
  val teacher: String? = null,
  val semester: String? = null,
  val lectures: Int? = null,
  val practice: Int? = null,
  val labs: Int? = null,
  @SerialName("self_study") val selfStudy: Int? = null,
  val contact: Int? = null,
  @SerialName("contact_session") val contactSession: Int? = null,
  @SerialName("hours_total") val hoursTotal: Int? = null,
  val elective: Boolean = false,
  val body: String? = null
) {
  val isExam: Boolean
    get() = control?.contains("экзамен", ignoreCase = true) == true

  val isCredit: Boolean
    get() =
      control?.contains("зачёт", ignoreCase = true) == true ||
        control?.contains("зачет", ignoreCase = true) == true
}

@Serializable
data class StudyTask(
  val title: String = "",
  val subject: String? = null,
  val type: String? = null,
  val deadline: String? = null,
  val status: String = TODO,
  val grade: String? = null,
  val body: String? = null
) {
  val normalizedStatus: String
    get() = if (status in TASK_STATUSES) status else TODO

  val shortTitle: String
    get() = title.substringAfter(" — ", title)

  val fullTitle: String
    get() =
      if (subject.isNullOrBlank() || title.contains(" — "))
        title.replace(" — ", ": ")
      else
        "$subject: $title"
}

internal fun subjects(ctx: PluginContext) = ctx.data.collection(StudyPlugin.SUBJECTS, Subject.serializer())

internal fun tasks(ctx: PluginContext) = ctx.data.collection(StudyPlugin.TASKS, StudyTask.serializer())

fun activeSorted(list: List<Item<StudyTask>>): List<Item<StudyTask>> =
  list
    .filter { it.value.normalizedStatus != SUBMITTED }
    .sortedBy { deadlineInfo(it.value.deadline.toDateOrNull()).days }

@Composable
private fun NearestDeadlines(ctx: PluginContext) {
  val all by remember(ctx) { tasks(ctx).observe() }.collectAsState(emptyList())
  val nearest = activeSorted(all).take(5)
  if (nearest.isEmpty()) EmptyHint(StudyText.noDeadlines())
  nearest.forEach { item ->
    val info = deadlineInfo(item.value.deadline.toDateOrNull())
    Row(
      Modifier.fillMaxWidth()
        .interactive(bleed = 6.dp) {
          ctx.nav.openPlugin(StudyPlugin.id, StudyPlugin.TASK_ROUTE + item.id)
        }
        .padding(vertical = 4.dp)
    ) {
      Text(item.value.fullTitle, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(info.label, color = info.tone.color(), style = MaterialTheme.typography.labelLarge)
    }
  }
}
