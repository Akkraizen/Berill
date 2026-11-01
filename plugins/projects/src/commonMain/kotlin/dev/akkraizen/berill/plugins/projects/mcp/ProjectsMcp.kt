package dev.akkraizen.berill.plugins.projects.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import dev.akkraizen.berill.plugins.projects.data.*

internal class ProjectsMcp(ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, ProjectsPlugin.PROJECTS, Project.serializer(), fieldSchemas = mapOf("status" to mcpEnumSchema(ProjectStatus.serializer(), ProjectStatus.entries)), normalize = { old, next -> if (old != null && old.status != next.status) next.moveTo(next.status) else next }, validate = { require(it.title.isNotBlank()) { "Project title is required" } })
  )
  override val collections: List<McpCollection> = editable + (ProjectsPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}
