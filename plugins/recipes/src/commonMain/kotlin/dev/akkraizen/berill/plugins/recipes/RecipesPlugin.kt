package dev.akkraizen.berill.plugins.recipes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.BreakfastDining
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.FlexibleStringList
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import kotlinx.serialization.Serializable

object RecipesPlugin : BerillPlugin {
  const val RECIPES = "recipes"

  const val IMAGES = "recipe_images"

  override val id = "recipes"
  override val title
    get() = RecipesText.title()

  override val icon = Icons.Outlined.RestaurantMenu
  override val order = 50
  override val description
    get() = RecipesText.description()

  override val installedByDefault = true
  override val collections = listOf(RECIPES, IMAGES)

  override val permissions = PluginPermissions(anyImageHost = true)

  internal const val RECIPE_ROUTE = "recipe:"

  @Composable
  override fun Screen(ctx: PluginContext) {
    val route by ctx.nav.route.collectAsState()
    val r = route

    AnimatedRoute(r) { r ->
      if (r != null && r.startsWith(RECIPE_ROUTE))
        RecipeScreen(ctx, r.removePrefix(RECIPE_ROUTE))
      else
        RecipesGallery(ctx)
    }
  }
}

val CATEGORY_ICONS: Map<String, ImageVector> = linkedMapOf(
  "Завтраки" to Icons.Outlined.BreakfastDining,
  "Супы" to Icons.Outlined.RamenDining,
  "Основные блюда" to Icons.Outlined.DinnerDining,
  "Гарниры" to Icons.Outlined.RiceBowl,
  "Салаты" to Icons.Outlined.Spa,
  "Перекусы" to Icons.Outlined.LunchDining,
  "Выпечка" to Icons.Outlined.BakeryDining,
  "Десерты" to Icons.Outlined.Cake,
  "Напитки" to Icons.Outlined.LocalCafe,
  "Соусы" to Icons.Outlined.WaterDrop
)

fun categoryIcon(category: String?): ImageVector = CATEGORY_ICONS[category] ?: Icons.Outlined.Restaurant

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
