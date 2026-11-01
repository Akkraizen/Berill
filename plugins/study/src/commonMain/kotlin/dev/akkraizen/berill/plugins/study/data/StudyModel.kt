package dev.akkraizen.berill.plugins.study.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.observeStringList
import dev.akkraizen.berill.api.stringList
import dev.akkraizen.berill.api.ui.deadlineInfo
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.study.StudyPlugin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val TODO = TaskStatus.TODO
val DOING = TaskStatus.DOING
val SUBMITTED = TaskStatus.SUBMITTED
val TASK_STATUSES = listOf(TODO, DOING, SUBMITTED)

fun nextStatus(status: TaskStatus) = TASK_STATUSES[(TASK_STATUSES.indexOf(status).coerceAtLeast(0) + 1) % TASK_STATUSES.size]

internal const val TASK_TYPES_KEY = "taskTypes"

@Composable
internal fun rememberTaskTypes(ctx: PluginContext): List<String> {
  val custom by
    remember(ctx) { ctx.settings.observeStringList(TASK_TYPES_KEY) }
      .collectAsState(ctx.settings.stringList(TASK_TYPES_KEY))
  return custom?.takeIf { it.isNotEmpty() } ?: TASK_TYPES
}

val TASK_TYPES: List<String> get() = dev.akkraizen.berill.plugins.study.i18n.pluginI18n.list("study.defaultTaskTypes")

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
    get() = SubjectControl.EXAM.matches(control)

  val isCredit: Boolean
    get() =
      SubjectControl.CREDIT.matches(control)
}

@Serializable
data class StudyTask(
  val title: String = "",
  val subject: String? = null,
  val type: String? = null,
  val deadline: String? = null,
  val status: TaskStatus = TODO,
  val grade: String? = null,
  val body: String? = null
) {
  val normalizedStatus: TaskStatus
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
