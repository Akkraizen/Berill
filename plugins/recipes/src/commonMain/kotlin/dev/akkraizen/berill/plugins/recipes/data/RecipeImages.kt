package dev.akkraizen.berill.plugins.recipes.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.plugins.recipes.RecipesPlugin
import kotlinx.serialization.Serializable

@Serializable
data class RecipeImage(
  val recipe: String = "",
  val data: String = "",
  val mime: String = "image/jpeg",
  val createdAt: Long = 0,
  val position: Long? = null
)

internal fun recipeImages(ctx: PluginContext) = ctx.data.collection(RecipesPlugin.IMAGES, RecipeImage.serializer())

@Composable
internal fun rememberRecipeImages(ctx: PluginContext): Map<String, List<Item<RecipeImage>>> {
  val collection = remember(ctx) { recipeImages(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  return remember(all) {
    all.groupBy { it.value.recipe }.mapValues { (_, list) -> orderedRecipeImages(list) }
  }
}

internal fun orderedRecipeImages(images: List<Item<RecipeImage>>): List<Item<RecipeImage>> =
  images.sortedWith(compareBy<Item<RecipeImage>> { it.value.position ?: it.value.createdAt }
    .thenBy { it.value.createdAt }.thenBy { it.id })

internal fun moveRecipeImage(images: List<Item<RecipeImage>>, id: String, target: Int): List<Item<RecipeImage>> {
  val from = images.indexOfFirst { it.id == id }
  if (from < 0) return images
  return images.toMutableList().apply { add(target.coerceIn(0, lastIndex), removeAt(from)) }
}

internal fun deleteRecipeImages(ctx: PluginContext, recipeId: String) {
  val collection = recipeImages(ctx)
  collection.all().filter { it.value.recipe == recipeId }.forEach { collection.delete(it.id) }
}
