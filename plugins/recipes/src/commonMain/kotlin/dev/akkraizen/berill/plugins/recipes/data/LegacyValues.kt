package dev.akkraizen.berill.plugins.recipes.data

/**
 * Values stored in synced records by older versions (and imported from Obsidian). They are data,
 * not interface text: never translate or edit them, or existing records stop matching.
 * This file is exempt from the hard-coded Cyrillic check (verifySourceLocalization).
 */
internal object LegacyValues {
  val recipeCategory: Map<RecipeCategory, String> = mapOf(
    RecipeCategory.BREAKFAST to "Завтраки",
    RecipeCategory.SOUP to "Супы",
    RecipeCategory.MAIN to "Основные блюда",
    RecipeCategory.SIDE to "Гарниры",
    RecipeCategory.SALAD to "Салаты",
    RecipeCategory.SNACK to "Перекусы",
    RecipeCategory.BAKING to "Выпечка",
    RecipeCategory.DESSERT to "Десерты",
    RecipeCategory.DRINK to "Напитки",
    RecipeCategory.SAUCE to "Соусы",
  )
}
