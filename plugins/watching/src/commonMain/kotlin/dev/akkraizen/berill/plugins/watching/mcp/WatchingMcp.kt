package dev.akkraizen.berill.plugins.watching.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.watching.WatchingPlugin
import dev.akkraizen.berill.plugins.watching.data.*

internal class WatchingMcp(ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, WatchingPlugin.TITLES, Title.serializer(), fieldSchemas = mapOf("status" to mcpEnumSchema(WatchStatus.serializer(), WatchStatus.entries), "media" to mcpEnumSchema(MediaKind.serializer(), MediaKind.entries)), normalize = { old, next -> if (old == null || old.status != next.status) next.moveTo(next.status) else next }, validate = { require(it.title.isNotBlank()) { "Title is required" } })
  )
  override val collections: List<McpCollection> = editable + (WatchingPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}
