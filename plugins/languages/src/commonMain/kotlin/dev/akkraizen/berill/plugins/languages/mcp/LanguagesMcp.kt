package dev.akkraizen.berill.plugins.languages.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.languages.LanguagesPlugin
import dev.akkraizen.berill.plugins.languages.data.*

internal class LanguagesMcp(ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, LanguagesPlugin.WORDS, Word.serializer(), fieldSchemas = mapOf("section" to mcpEnumSchema(WordSection.serializer(), WordSection.entries)), validate = { require(it.word.isNotBlank() && it.translation.isNotBlank()) { "Word and translation are required" } }),
    TypedMcpCollection(ctx, LanguagesPlugin.LESSONS, Lesson.serializer(), validate = { require(it.title.isNotBlank()) { "Lesson title is required" } })
  )
  override val collections: List<McpCollection> = editable + (LanguagesPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}
