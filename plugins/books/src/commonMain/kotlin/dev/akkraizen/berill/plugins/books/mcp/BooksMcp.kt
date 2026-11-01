package dev.akkraizen.berill.plugins.books.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.books.BooksPlugin
import dev.akkraizen.berill.plugins.books.data.*

internal class BooksMcp(ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, BooksPlugin.BOOKS, Book.serializer(), fieldSchemas = mapOf("status" to mcpEnumSchema(BookStatus.serializer(), BookStatus.entries)), normalize = { old, next -> if (old == null || old.status != next.status) next.moveTo(next.status) else next }, validate = { require(it.title.isNotBlank()) { "Book title is required" } })
  )
  override val collections: List<McpCollection> = editable + (BooksPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}
