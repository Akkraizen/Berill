package dev.akkraizen.berill.plugins.recipes.data
/** Built-in choices; custom recipe category names remain free text. */
enum class RecipeCategory { BREAKFAST, SOUP, MAIN, SIDE, SALAD, SNACK, BAKING, DESSERT, DRINK, SAUCE;
 val wire: String get() = LegacyValues.recipeCategory.getValue(this)
 companion object { fun parse(value: String?): RecipeCategory? = entries.firstOrNull { it.name == value || it.wire == value } }
}
internal enum class RecipeFilter { ALL }
