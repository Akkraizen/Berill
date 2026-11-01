package dev.akkraizen.berill.plugins.study.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.study.StudyPlugin
import dev.akkraizen.berill.plugins.study.data.*

internal class StudyMcp(ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, StudyPlugin.SUBJECTS, Subject.serializer(), validate = { require(it.title.isNotBlank()) { "Subject title required" } }),
    TypedMcpCollection(ctx, StudyPlugin.TASKS, StudyTask.serializer(), fieldSchemas = mapOf("status" to mcpEnumSchema(TaskStatus.serializer(), TaskStatus.entries)), validate = { require(it.title.isNotBlank() && (it.deadline == null || it.deadline.toDateOrNull() != null)) { "Task title and valid deadline required" } })
  )
  override val collections: List<McpCollection> = editable + (StudyPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}
