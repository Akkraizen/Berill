package dev.akkraizen.berill.plugins.projects.mcp

import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import dev.akkraizen.berill.plugins.projects.data.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class ProjectsMcp(private val ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId

  private fun projectExists(id: String) = projects(ctx).get(id) != null

  private fun validDate(value: String?) = value == null || value.toDateOrNull() != null

  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(
      ctx, ProjectsPlugin.PROJECTS, Project.serializer(),
      fieldSchemas = mapOf("status" to mcpEnumSchema(ProjectStatus.serializer(), ProjectStatus.entries)),
      normalize = { old, next -> (if (old != null && old.status != next.status) next.moveTo(next.status) else next).copy(tags = normalizeTags(next.tags)) },
      validate = { require(it.title.isNotBlank() && validDate(it.deadline)) { "Project title and a valid deadline (YYYY-MM-DD) are required" } }
    ),
    TypedMcpCollection(
      ctx, ProjectsPlugin.TASKS, ProjectTask.serializer(),
      fieldSchemas = mapOf(
        "status" to mcpEnumSchema(TaskStatus.serializer(), TaskStatus.entries),
        "priority" to mcpEnumSchema(TaskPriority.serializer(), TaskPriority.entries)
      ),
      normalize = { old, next ->
        val moved = if (old?.status != next.status) next.moveTo(next.status) else next
        if (old == null) moved.copy(created = moved.created ?: today().toString(), order = if (moved.order == 0L) nextOrder(projectTasks(ctx).all().filter { it.value.project == moved.project }.map { it.value.order }) else moved.order) else moved
      },
      validate = { task ->
        require(task.title.isNotBlank()) { "Task title is required" }
        require(projectExists(task.project)) { "Task project must be an existing project id" }
        require(validDate(task.due)) { "Due date must be YYYY-MM-DD" }
        require(task.milestone == null || milestones(ctx).get(task.milestone)?.project == task.project) { "Milestone must belong to the same project" }
      }
    ),
    TypedMcpCollection(
      ctx, ProjectsPlugin.MILESTONES, Milestone.serializer(),
      normalize = { old, next -> if (old?.done != next.done) next.withDone(next.done) else next },
      validate = { step ->
        require(step.title.isNotBlank()) { "Milestone title is required" }
        require(projectExists(step.project)) { "Milestone project must be an existing project id" }
        require(validDate(step.due)) { "Due date must be YYYY-MM-DD" }
      }
    )
  )
  override val collections: List<McpCollection> = editable + (ProjectsPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }

  override val actions: List<McpAction> = listOf(
    McpAction(
      "overview",
      "Summarizes projects: status, tags, deadline, task progress and the next open tasks. With `project` it returns that " +
        "project in full with every task, milestone and linked note title.",
      mcpInput {
        string("project", "Project id or exact title")
        boolean("include_archived", "Also list archived projects")
      },
      readOnly = true
    ) { args -> overview(args) },
    McpAction(
      "add_task",
      "Adds a task to a project. Tasks with a due date appear in the planner calendar and the daily overview.",
      mcpInput {
        string("project", "Project id or exact title", required = true)
        string("title", "Task title", required = true)
        string("due", "Due date YYYY-MM-DD")
        choice("priority", "Priority", TaskPriority.entries.map { it.name })
        string("milestone", "Milestone id or exact title in this project")
        string("details", "Optional details")
      }
    ) { args -> addTask(args) },
    McpAction(
      "set_task_status",
      "Moves a project task to TODO, DOING or DONE; DONE records the completion date.",
      mcpInput {
        string("task", "Task id", required = true)
        choice("status", "New status", TaskStatus.entries.map { it.name }, required = true)
      }
    ) { args -> setTaskStatus(args) },
    McpAction(
      "move_project",
      "Moves a project on the board (IDEA, IN_PROGRESS, PAUSED, READY) with the app's start and finish dates, or archives it.",
      mcpInput {
        string("project", "Project id or exact title", required = true)
        choice("status", "New board column", ProjectStatus.entries.map { it.name })
        boolean("archived", "Archive or restore the project")
      }
    ) { args -> moveProject(args) },
  )

  private fun project(reference: String): Item<Project> {
    val all = projects(ctx).all()
    return all.firstOrNull { it.id == reference }
      ?: all.filter { it.value.title.equals(reference.trim(), ignoreCase = true) }.let { matches ->
        when (matches.size) {
          1 -> matches.single()
          0 -> throw McpException("Project not found", McpErrorCode.NOT_FOUND)
          else -> throw McpException("Several projects are called `$reference`; pass the id")
        }
      }
  }

  private fun taskJson(item: Item<ProjectTask>): JsonObject = buildJsonObject {
    val t = item.value
    put("id", item.id)
    put("title", t.title)
    put("status", t.status.name)
    put("priority", t.priority.name)
    t.due?.let { put("due", it) }
    t.milestone?.let { put("milestone", it) }
    t.completed?.let { put("completed", it) }
    if (t.overdue()) put("overdue", true)
    t.details?.let { put("details", it) }
  }

  private fun overview(args: JsonObject): JsonElement {
    val tasks = projectTasks(ctx).all()
    val steps = milestones(ctx).all()
    val reference = args.mcpOptionalString("project")?.takeIf { it.isNotBlank() }
    if (reference != null) {
      val item = project(reference)
      val p = item.value
      val own = tasks.filter { it.value.project == item.id }.sortedWith(TASK_ORDER)
      val progress = own.map { it.value }.progress()
      return buildJsonObject {
        put("id", item.id)
        put("title", p.title)
        put("status", p.normalizedStatus.name)
        if (p.archived) put("archived", true)
        p.description?.let { put("description", it) }
        put("tags", JsonArray(p.tags.map(::JsonPrimitive)))
        put("stack", JsonArray(p.stack.map(::JsonPrimitive)))
        p.deadline?.let { put("deadline", it) }
        p.started?.let { put("started", it) }
        p.finished?.let { put("finished", it) }
        put("progress", buildJsonObject { put("done", progress.done); put("total", progress.total) })
        put("milestones", buildJsonArray {
          steps.filter { it.value.project == item.id }.sortedWith(MILESTONE_ORDER).forEach { step ->
            val inStep = own.filter { it.value.milestone == step.id }.map { it.value }.progress()
            add(buildJsonObject {
              put("id", step.id)
              put("title", step.value.title)
              step.value.due?.let { put("due", it) }
              put("done", step.value.done)
              put("tasks", buildJsonObject { put("done", inStep.done); put("total", inStep.total) })
            })
          }
        })
        put("tasks", JsonArray(own.map(::taskJson)))
        put("notes", buildJsonArray {
          p.notes.forEach { id -> add(buildJsonObject { put("id", id); ctx.notes.get(id)?.let { put("title", it.title) } }) }
        })
        p.body?.let { put("body", it) }
      }
    }
    val includeArchived = args.mcpOptionalBoolean("include_archived") == true
    return buildJsonObject {
      put("projects", buildJsonArray {
        projects(ctx).all().filter { includeArchived || !it.value.archived }.sortedBy { it.value.normalizedStatus.ordinal }.forEach { item ->
          val own = tasks.filter { it.value.project == item.id }
          val progress = own.map { it.value }.progress()
          add(buildJsonObject {
            put("id", item.id)
            put("title", item.value.title)
            put("status", item.value.normalizedStatus.name)
            if (item.value.archived) put("archived", true)
            if (item.value.tags.isNotEmpty()) put("tags", JsonArray(item.value.tags.map(::JsonPrimitive)))
            item.value.deadline?.let { put("deadline", it) }
            put("progress", buildJsonObject { put("done", progress.done); put("total", progress.total) })
            put("nextTasks", JsonArray(own.filterNot { it.value.done }.sortedWith(TASK_ORDER).take(3).map(::taskJson)))
          })
        }
      })
    }
  }

  private fun addTask(args: JsonObject): JsonElement {
    val item = project(args.mcpString("project"))
    val due = args.mcpOptionalString("due")?.trim()?.takeIf { it.isNotEmpty() }
    if (!validDate(due)) throw McpException("Due date must be YYYY-MM-DD")
    val priority = args.mcpOptionalString("priority")?.let { name -> TaskPriority.entries.firstOrNull { it.name == name } ?: throw McpException("Unknown priority") } ?: TaskPriority.NORMAL
    val milestone = args.mcpOptionalString("milestone")?.takeIf { it.isNotBlank() }?.let { reference ->
      val own = milestones(ctx).all().filter { it.value.project == item.id }
      (own.firstOrNull { it.id == reference } ?: own.firstOrNull { it.value.title.equals(reference.trim(), ignoreCase = true) })?.id
        ?: throw McpException("Milestone not found in this project", McpErrorCode.NOT_FOUND)
    }
    val tasks = projectTasks(ctx)
    val id = ctx.data.newId()
    val task = ProjectTask(
      project = item.id,
      title = args.mcpString("title").trim(),
      priority = priority,
      due = due,
      milestone = milestone,
      details = args.mcpOptionalString("details")?.trim()?.takeIf { it.isNotEmpty() },
      order = nextOrder(tasks.all().filter { it.value.project == item.id }.map { it.value.order }),
      created = today().toString()
    )
    tasks.save(id, task)
    return taskJson(Item(id, task))
  }

  private fun setTaskStatus(args: JsonObject): JsonElement {
    val tasks = projectTasks(ctx)
    val id = args.mcpString("task")
    val task = tasks.get(id) ?: throw McpException("Task not found", McpErrorCode.NOT_FOUND)
    val status = TaskStatus.entries.firstOrNull { it.name == args.mcpString("status") } ?: throw McpException("Unknown status")
    val next = task.moveTo(status)
    tasks.save(id, next)
    return taskJson(Item(id, next))
  }

  private fun moveProject(args: JsonObject): JsonElement {
    val item = project(args.mcpString("project"))
    val status = args.mcpOptionalString("status")?.let { name -> ProjectStatus.entries.firstOrNull { it.name == name } ?: throw McpException("Unknown status") }
    val archived = args.mcpOptionalBoolean("archived")
    if (status == null && archived == null) throw McpException("Pass status or archived")
    val collection = projects(ctx)
    val fresh = collection.get(item.id) ?: throw McpException("Project not found", McpErrorCode.NOT_FOUND)
    val next = (status?.let { fresh.moveTo(it) } ?: fresh).let { p -> archived?.let { p.copy(archived = it) } ?: p }
    collection.save(item.id, next)
    return overview(buildJsonObject { put("project", item.id) })
  }
}
