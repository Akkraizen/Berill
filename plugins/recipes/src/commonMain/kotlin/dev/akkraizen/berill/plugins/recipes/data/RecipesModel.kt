package dev.akkraizen.berill.plugins.recipes.data

import dev.akkraizen.berill.api.FlexibleStringList
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.plugins.recipes.RecipesPlugin
import kotlinx.serialization.Serializable

@Serializable
data class Recipe(
  val title: String = "",
  val category: String? = null,
  val time: Int? = null,
  val servings: String? = null,
  val cover: String? = null,
  val favorite: Boolean = false,
  @Serializable(with = FlexibleStringList::class) val tags: List<String> = emptyList(),
  val body: String? = null
)

internal fun recipes(ctx: PluginContext) = ctx.data.collection(RecipesPlugin.RECIPES, Recipe.serializer())

internal const val ALL = "все"
