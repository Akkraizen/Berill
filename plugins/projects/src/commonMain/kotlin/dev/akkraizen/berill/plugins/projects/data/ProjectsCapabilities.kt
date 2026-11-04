package dev.akkraizen.berill.plugins.projects.data

import dev.akkraizen.berill.api.DayEntry
import dev.akkraizen.berill.api.DaySummarySource
import dev.akkraizen.berill.api.Deadline
import dev.akkraizen.berill.api.DeadlineSource
import dev.akkraizen.berill.api.MaintenanceSource
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.SearchResult
import dev.akkraizen.berill.api.SearchSource
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import dev.akkraizen.berill.plugins.projects.i18n.ProjectsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

internal fun projectRoute(id: String) = ProjectsPlugin.PROJECT_ROUTE + id

internal class ProjectDeadlines(private val ctx: PluginContext) : DeadlineSource {
  override fun deadlines(): Flow<List<Deadline>> =
    combine(projects(ctx).observe(), projectTasks(ctx).observe(), milestones(ctx).observe()) { all, tasks, steps ->
      val open = all.filter { !it.value.archived && it.value.normalizedStatus != READY }.associateBy { it.id }
      val fromProjects = open.values.mapNotNull { item ->
        item.value.deadline.toDateOrNull()?.let { Deadline(item.value.title, it, ctx.pluginId, projectRoute(item.id)) }
      }
      val fromSteps = steps.filter { !it.value.done && it.value.project in open }.mapNotNull { item ->
        val project = open.getValue(item.value.project).value
        item.value.due.toDateOrNull()?.let { Deadline("${project.title} · ${item.value.title}", it, ctx.pluginId, projectRoute(item.value.project)) }
      }
      val fromTasks = tasks.filter { !it.value.done && it.value.project in open }.mapNotNull { item ->
        val project = open.getValue(item.value.project).value
        item.value.due.toDateOrNull()?.let { Deadline("${project.title} · ${item.value.title}", it, ctx.pluginId, projectRoute(item.value.project)) }
      }
      fromProjects + fromSteps + fromTasks
    }
}

internal class ProjectDays(private val ctx: PluginContext) : DaySummarySource {
  override suspend fun entries(date: LocalDate): List<DayEntry> {
    val day = date.toString()
    val titles = projects(ctx).all().associate { it.id to it.value.title }
    return projectTasks(ctx).all()
      .filter { it.value.project in titles && (it.value.completed == day || (!it.value.done && it.value.due == day)) }
      .sortedWith(TASK_ORDER)
      .map { item ->
        val state = if (item.value.done) ProjectsText.taskDone() else ProjectsText.taskDue()
        DayEntry("${ctx.pluginId}/${ProjectsPlugin.TASKS}/${item.id}", titles.getValue(item.value.project), "$state: ${item.value.title}") {
          ctx.nav.openPlugin(ctx.pluginId, projectRoute(item.value.project))
        }
      }
  }
}

internal class ProjectSearch(private val ctx: PluginContext) : SearchSource {
  override suspend fun search(query: String): List<SearchResult> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return emptyList()
    fun matches(vararg parts: String?) = parts.filterNotNull().joinToString(" ").lowercase().let { text -> terms.all { it in text } }
    val all = projects(ctx).all()
    val titles = all.associate { it.id to it.value.title }
    val found = all.filter { item ->
      val p = item.value
      matches(p.title, p.description, p.body, p.stack.joinToString(" "), p.tags.joinToString(" "))
    }.map { item ->
      SearchResult("${ctx.pluginId}/${ProjectsPlugin.PROJECTS}/${item.id}", item.value.title, ProjectsText.title()) {
        ctx.nav.openPlugin(ctx.pluginId, projectRoute(item.id))
      }
    }
    val tasks = projectTasks(ctx).all().filter { it.value.project in titles && matches(it.value.title, it.value.details) }.map { item ->
      SearchResult("${ctx.pluginId}/${ProjectsPlugin.TASKS}/${item.id}", item.value.title, "${ProjectsText.title()} · ${titles.getValue(item.value.project)}") {
        ctx.nav.openPlugin(ctx.pluginId, projectRoute(item.value.project))
      }
    }
    return (found + tasks).take(50)
  }
}

internal class ChecklistMigration(private val ctx: PluginContext) : MaintenanceSource {
  override suspend fun update() = migrateAllChecklists(ctx)
}

internal class ProjectsToday(private val ctx: PluginContext) : dev.akkraizen.berill.api.TodaySource {
  override fun today(date: LocalDate): Flow<List<dev.akkraizen.berill.api.TodayItem>> =
    combine(projects(ctx).observe(), projectTasks(ctx).observe(), milestones(ctx).observe()) { all, tasks, steps ->
      val day = date.toString()
      val open = all.filter { !it.value.archived }.associateBy { it.id }
      val taskItems = tasks.filter { item ->
        val task = item.value
        task.project in open && ((!task.done && task.due.toDateOrNull()?.let { it <= date } == true) || (task.done && task.completed == day))
      }.sortedWith(TASK_ORDER).map { item ->
        val task = item.value
        dev.akkraizen.berill.api.TodayItem(
          id = "${ctx.pluginId}/${ProjectsPlugin.TASKS}/${item.id}",
          title = task.title,
          kind = dev.akkraizen.berill.api.TodayKind.TASK,
          pluginId = ctx.pluginId,
          detail = open.getValue(task.project).value.title,
          done = task.done,
          overdue = task.overdue(date),
          toggle = {
            val tasksCollection = projectTasks(ctx)
            tasksCollection.get(item.id)?.let { current -> tasksCollection.save(item.id, current.moveTo(if (current.done) TaskStatus.TODO else TaskStatus.DONE)) }
          }
        ) { ctx.nav.openPlugin(ctx.pluginId, projectRoute(task.project)) }
      }
      val stepItems = steps.filter { item -> !item.value.done && item.value.project in open && item.value.due.toDateOrNull()?.let { it <= date } == true }.map { item ->
        dev.akkraizen.berill.api.TodayItem(
          id = "${ctx.pluginId}/${ProjectsPlugin.MILESTONES}/${item.id}",
          title = item.value.title,
          kind = dev.akkraizen.berill.api.TodayKind.DEADLINE,
          pluginId = ctx.pluginId,
          detail = open.getValue(item.value.project).value.title,
          done = false,
          overdue = item.value.due.toDateOrNull()?.let { it < date } == true,
          toggle = { milestones(ctx).let { log -> log.get(item.id)?.let { log.save(item.id, it.withDone(!it.done)) } } }
        ) { ctx.nav.openPlugin(ctx.pluginId, projectRoute(item.value.project)) }
      }
      val projectItems = open.values.filter { it.value.normalizedStatus != READY && it.value.deadline.toDateOrNull()?.let { d -> d <= date } == true }.map { item ->
        dev.akkraizen.berill.api.TodayItem(
          id = "${ctx.pluginId}/${ProjectsPlugin.PROJECTS}/${item.id}",
          title = item.value.title,
          kind = dev.akkraizen.berill.api.TodayKind.DEADLINE,
          pluginId = ctx.pluginId,
          detail = ProjectsText.title(),
          overdue = item.value.deadline.toDateOrNull()?.let { it < date } == true
        ) { ctx.nav.openPlugin(ctx.pluginId, projectRoute(item.id)) }
      }
      taskItems + stepItems + projectItems
    }
}
