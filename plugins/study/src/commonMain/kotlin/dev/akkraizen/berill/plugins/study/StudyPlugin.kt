package dev.akkraizen.berill.plugins.study

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.School
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.Capability
import dev.akkraizen.berill.api.Deadline
import dev.akkraizen.berill.api.DeadlineSource
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.setStringList
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.fields.EditableList
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.study.data.SUBMITTED
import dev.akkraizen.berill.plugins.study.data.TASK_TYPES
import dev.akkraizen.berill.plugins.study.data.TASK_TYPES_KEY
import dev.akkraizen.berill.plugins.study.data.rememberTaskTypes
import dev.akkraizen.berill.plugins.study.data.tasks
import dev.akkraizen.berill.plugins.study.i18n.StudyText
import dev.akkraizen.berill.plugins.study.ui.NearestDeadlines
import dev.akkraizen.berill.plugins.study.ui.StudyScreen
import dev.akkraizen.berill.plugins.study.ui.SubjectScreen
import dev.akkraizen.berill.plugins.study.ui.TaskScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dev.akkraizen.berill.plugins.study.widgets.studyWidgets

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

  override fun widgets(ctx: PluginContext) = studyWidgets(ctx)
}
