package dev.akkraizen.berill.plugins.projects.data

import dev.akkraizen.berill.api.DataCollection
import dev.akkraizen.berill.api.FlexibleStringList
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import dev.akkraizen.berill.plugins.projects.i18n.pluginI18n
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

val IDEA = ProjectStatus.IDEA
val IN_PROGRESS = ProjectStatus.IN_PROGRESS
val PAUSED = ProjectStatus.PAUSED
val READY = ProjectStatus.READY
val STATUSES = listOf(IDEA, IN_PROGRESS, PAUSED, READY)

@Serializable
data class Project(
  val title: String = "",
  val status: ProjectStatus = IDEA,
  val description: String? = null,
  @Serializable(with = FlexibleStringList::class) val stack: List<String> = emptyList(),
  val repo: String? = null,
  val site: String? = null,
  val created: String? = null,
  val started: String? = null,
  val finished: String? = null,
  val body: String? = null,
  @Serializable(with = FlexibleStringList::class) val tags: List<String> = emptyList(),
  val deadline: String? = null,
  val archived: Boolean = false,
  @Serializable(with = FlexibleStringList::class) val notes: List<String> = emptyList(),
  val tasksMigrated: Boolean = false
) {
  val normalizedStatus: ProjectStatus
    get() = if (status in STATUSES) status else IDEA

  fun moveTo(target: ProjectStatus): Project {
    val now = today().toString()
    return when (target) {
      IN_PROGRESS -> copy(status = target, started = started ?: now, finished = null)
      READY -> copy(status = target, started = started ?: now, finished = finished ?: now)
      else -> copy(status = target, finished = null)
    }
  }
}

@Serializable
enum class TaskStatus { TODO, DOING, DONE }

@Serializable
enum class TaskPriority { LOW, NORMAL, HIGH }

@Serializable
data class ProjectTask(
  val project: String = "",
  val title: String = "",
  val status: TaskStatus = TaskStatus.TODO,
  val priority: TaskPriority = TaskPriority.NORMAL,
  val due: String? = null,
  val milestone: String? = null,
  val note: String? = null,
  val details: String? = null,
  val order: Long = 0,
  val created: String? = null,
  val completed: String? = null
) {
  val done: Boolean get() = status == TaskStatus.DONE

  fun moveTo(target: TaskStatus): ProjectTask =
    if (target == TaskStatus.DONE) copy(status = target, completed = completed ?: today().toString())
    else copy(status = target, completed = null)

  fun overdue(on: LocalDate = today()): Boolean = !done && (due.toDateOrNull()?.let { it < on } ?: false)
}

@Serializable
data class Milestone(
  val project: String = "",
  val title: String = "",
  val due: String? = null,
  val done: Boolean = false,
  val order: Long = 0,
  val completed: String? = null
) {
  fun withDone(value: Boolean): Milestone = copy(done = value, completed = if (value) completed ?: today().toString() else null)
}

data class Progress(val done: Int, val total: Int) {
  val fraction: Float get() = if (total == 0) 0f else done / total.toFloat()
}

fun List<ProjectTask>.progress(): Progress = Progress(count { it.done }, size)

val TASK_ORDER: Comparator<Item<ProjectTask>> =
  compareBy<Item<ProjectTask>>({ it.value.done }, { -it.value.priority.ordinal }, { it.value.due ?: "9999" }, { it.value.order })

val MILESTONE_ORDER: Comparator<Item<Milestone>> = compareBy({ it.value.due ?: "9999" }, { it.value.order })

fun normalizeTags(values: List<String>): List<String> =
  values.map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

data class CheckItem(val line: Int, val text: String, val done: Boolean)

private val CHECK = Regex("""^\s*[-*] \[([ xX])\] (.*)$""")

fun checklist(body: String?): List<CheckItem> =
  body.orEmpty().lines().mapIndexedNotNull { i, line ->
    CHECK.matchEntire(line)?.let { m -> CheckItem(i, m.groupValues[2], m.groupValues[1] != " ") }
  }

private fun isTaskHeading(line: String): Boolean =
  pluginI18n.tr("projects.taskHeading").translations.values.any { line.trim().equals(it.trim(), ignoreCase = true) }

fun withoutChecklist(body: String?): String? {
  val lines = body.orEmpty().lines().filterNot { CHECK.matches(it) }.toMutableList()
  val heading = lines.indexOfFirst(::isTaskHeading)
  if (heading >= 0) {
    val next = (heading + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
    if ((heading + 1 until next).all { lines[it].isBlank() }) for (i in next - 1 downTo heading) lines.removeAt(i)
  }
  return lines.joinToString("\n").replace(Regex("\n{4,}"), "\n\n\n").trim('\n').ifBlank { null }
}

fun migrationTaskId(projectId: String, index: Int) = "$projectId.check$index"

internal fun migrateChecklist(ctx: PluginContext, projectId: String) {
  val collection = projects(ctx)
  val project = collection.get(projectId) ?: return
  if (project.tasksMigrated) return
  val tasks = projectTasks(ctx)
  checklist(project.body).forEachIndexed { index, item ->
    val id = migrationTaskId(projectId, index)
    if (tasks.get(id) == null && item.text.isNotBlank())
      tasks.save(id, ProjectTask(projectId, item.text.trim(), if (item.done) TaskStatus.DONE else TaskStatus.TODO, order = index.toLong(), created = project.created))
  }
  val fresh = collection.get(projectId) ?: return
  collection.save(projectId, fresh.copy(body = withoutChecklist(fresh.body), tasksMigrated = true))
}

internal fun migrateAllChecklists(ctx: PluginContext) {
  projects(ctx).all().filterNot { it.value.tasksMigrated }.forEach { migrateChecklist(ctx, it.id) }
}

internal fun deleteProject(ctx: PluginContext, id: String) {
  projectTasks(ctx).let { tasks -> tasks.all().filter { it.value.project == id }.forEach { tasks.delete(it.id) } }
  milestones(ctx).let { list -> list.all().filter { it.value.project == id }.forEach { list.delete(it.id) } }
  projects(ctx).delete(id)
}

internal fun deleteMilestone(ctx: PluginContext, id: String) {
  val tasks = projectTasks(ctx)
  tasks.all().filter { it.value.milestone == id }.forEach { tasks.save(it.id, it.value.copy(milestone = null)) }
  milestones(ctx).delete(id)
}

internal fun nextOrder(items: List<Long>): Long = (items.maxOrNull() ?: -1) + 1

val TEMPLATE: String get() = pluginI18n.tr("projects.template")()

internal fun projects(ctx: PluginContext): DataCollection<Project> =
  ctx.data.collection(ProjectsPlugin.PROJECTS, Project.serializer())

internal fun projectTasks(ctx: PluginContext): DataCollection<ProjectTask> =
  ctx.data.collection(ProjectsPlugin.TASKS, ProjectTask.serializer())

internal fun milestones(ctx: PluginContext): DataCollection<Milestone> =
  ctx.data.collection(ProjectsPlugin.MILESTONES, Milestone.serializer())
