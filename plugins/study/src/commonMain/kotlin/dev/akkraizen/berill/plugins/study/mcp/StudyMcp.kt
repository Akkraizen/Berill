package dev.akkraizen.berill.plugins.study.mcp

import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.study.StudyPlugin
import dev.akkraizen.berill.plugins.study.data.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class StudyMcp(private val ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, StudyPlugin.SUBJECTS, Subject.serializer(), validate = { require(it.title.isNotBlank()) { "Subject title required" } }),
    TypedMcpCollection(ctx, StudyPlugin.TASKS, StudyTask.serializer(), fieldSchemas = mapOf("status" to mcpEnumSchema(TaskStatus.serializer(), TaskStatus.entries)), validate = { require(it.title.isNotBlank() && (it.deadline == null || it.deadline.toDateOrNull() != null)) { "Task title and valid deadline required" } })
  )
  override val collections: List<McpCollection> = editable + (StudyPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }

  override val actions: List<McpAction> = listOf(
    McpAction(
      "upcoming",
      "Lists unsubmitted assignments with a deadline in the next `days` days (default 7), plus every overdue one, " +
        "sorted by deadline with the subject title and days left.",
      mcpInput { integer("days", "How many days ahead to look", min = 0, max = 365) },
      readOnly = true
    ) { args -> upcoming(args) },
    McpAction(
      "add_deadline",
      "Adds an assignment with a deadline to a subject. The subject is matched by id or exact title and created when missing.",
      mcpInput {
        string("subject", "Subject id or exact title", required = true)
        string("title", "Assignment title", required = true)
        string("deadline", "Deadline YYYY-MM-DD", required = true)
        string("type", "Optional assignment type, for example a lab or an essay")
        string("details", "Optional details")
      }
    ) { args -> addDeadline(args) },
    McpAction(
      "set_status",
      "Moves an assignment to TODO, DOING or SUBMITTED.",
      mcpInput {
        string("task", "Assignment id", required = true)
        choice("status", "New status", TaskStatus.entries.map { it.name }, required = true)
        string("grade", "Optional grade to record")
      }
    ) { args -> setStatus(args) }
  )

  private fun subjectTitles(): Map<String, String> = subjects(ctx).all().associate { it.id to it.value.title }

  private fun describe(item: Item<StudyTask>, titles: Map<String, String>): JsonObject = buildJsonObject {
    val task = item.value
    put("id", item.id)
    put("title", task.title)
    task.subject?.let { id -> put("subjectId", id); titles[id]?.let { put("subject", it) } }
    task.type?.let { put("type", it) }
    task.deadline?.let { deadline ->
      put("deadline", deadline)
      deadline.toDateOrNull()?.let { put("daysLeft", today().daysUntil(it)) }
    }
    put("status", task.normalizedStatus.name)
    task.grade?.let { put("grade", it) }
  }

  private fun upcoming(args: JsonObject): JsonElement {
    val days = args.mcpOptionalInt("days", 0..365) ?: 7
    val until = today().plus(days, DateTimeUnit.DAY)
    val titles = subjectTitles()
    val due = tasks(ctx).all()
      .filter { it.value.normalizedStatus != TaskStatus.SUBMITTED }
      .mapNotNull { item -> item.value.deadline.toDateOrNull()?.takeIf { it <= until }?.let { item to it } }
      .sortedBy { it.second }
    return buildJsonObject {
      put("today", today().toString())
      put("overdue", due.count { it.second < today() })
      put("tasks", JsonArray(due.map { describe(it.first, titles) }))
    }
  }

  private fun resolveSubject(reference: String): String {
    val all = subjects(ctx).all()
    all.firstOrNull { it.id == reference }?.let { return it.id }
    all.firstOrNull { it.value.title.trim().equals(reference.trim(), ignoreCase = true) }?.let { return it.id }
    val id = ctx.data.newId()
    subjects(ctx).save(id, Subject(title = reference.trim()))
    return id
  }

  private fun addDeadline(args: JsonObject): JsonElement {
    val deadline = args.mcpString("deadline").toDateOrNull() ?: throw McpException("`deadline` must be YYYY-MM-DD")
    val subject = resolveSubject(args.mcpString("subject"))
    val id = ctx.data.newId()
    val task = StudyTask(
      title = args.mcpString("title").trim(),
      subject = subject,
      type = args.mcpOptionalString("type")?.trim()?.takeIf { it.isNotEmpty() },
      deadline = deadline.toString(),
      body = args.mcpOptionalString("details")?.trim()?.takeIf { it.isNotEmpty() }
    )
    tasks(ctx).save(id, task)
    return describe(Item(id, task), subjectTitles())
  }

  private fun setStatus(args: JsonObject): JsonElement {
    val id = args.mcpString("task")
    val current = tasks(ctx).get(id) ?: throw McpException("Assignment not found")
    val status = runCatching { TaskStatus.valueOf(args.mcpString("status")) }.getOrNull() ?: throw McpException("Unknown status")
    val next = current.copy(status = status, grade = args.mcpOptionalString("grade")?.trim()?.takeIf { it.isNotEmpty() } ?: current.grade)
    tasks(ctx).save(id, next)
    return describe(Item(id, next), subjectTitles())
  }
}
