package dev.akkraizen.berill.plugins.recipes.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.BreakfastDining
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

val CATEGORY_ICONS: Map<dev.akkraizen.berill.plugins.recipes.data.RecipeCategory, ImageVector> = linkedMapOf(
  dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.BREAKFAST to Icons.Outlined.BreakfastDining,
  dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.SOUP to Icons.Outlined.RamenDining,
  dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.MAIN to Icons.Outlined.DinnerDining,
  dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.SIDE to Icons.Outlined.RiceBowl,
  dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.SALAD to Icons.Outlined.Spa,
  dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.SNACK to Icons.Outlined.LunchDining,
  dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.BAKING to Icons.Outlined.BakeryDining,
  dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.DESSERT to Icons.Outlined.Cake,
  dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.DRINK to Icons.Outlined.LocalCafe,
  dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.SAUCE to Icons.Outlined.WaterDrop
)

fun categoryIcon(category: String?): ImageVector = CATEGORY_ICONS[dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.parse(category)] ?: Icons.Outlined.Restaurant
