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
