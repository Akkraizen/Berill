package dev.akkraizen.berill.plugins.recipes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.plugins.recipes.data.recipes
import dev.akkraizen.berill.plugins.recipes.i18n.RecipesText
import dev.akkraizen.berill.plugins.recipes.ui.RecipeScreen
import dev.akkraizen.berill.plugins.recipes.ui.RecipesGallery
import dev.akkraizen.berill.plugins.recipes.widgets.recipesWidgets

object RecipesPlugin : BerillPlugin {
  override fun provides(ctx: dev.akkraizen.berill.api.PluginContext): List<dev.akkraizen.berill.api.Capability> = baseProvides(ctx) + dev.akkraizen.berill.plugins.recipes.mcp.RecipesMcp(ctx)

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
    androidx.compose.runtime.CompositionLocalProvider(dev.akkraizen.berill.plugins.recipes.ui.LocalRecipeFiles provides ctx.files) {
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

  private fun baseProvides(ctx: PluginContext): List<dev.akkraizen.berill.api.Capability> = listOf(dev.akkraizen.berill.api.CollectionSearchSource(ctx, RECIPES, { title }, { "recipe:$it" }))

  override fun widgets(ctx: PluginContext) = recipesWidgets(ctx)
}
