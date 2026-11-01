package dev.akkraizen.berill.plugins.recipes.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.recipes.RecipesPlugin
import dev.akkraizen.berill.plugins.recipes.data.*

internal class RecipesMcp(ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, RecipesPlugin.RECIPES, Recipe.serializer(), validate = { require(it.title.isNotBlank() && (it.time == null || it.time >= 0)) { "Recipe title and valid time are required" } })
  )
  override val collections: List<McpCollection> = editable + (RecipesPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}
