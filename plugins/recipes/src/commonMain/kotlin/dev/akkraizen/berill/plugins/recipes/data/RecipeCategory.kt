package dev.akkraizen.berill.plugins.recipes.data
import dev.akkraizen.berill.plugins.recipes.i18n.pluginI18n
/** Built-in choices; custom recipe category names remain free text. */
enum class RecipeCategory { BREAKFAST, SOUP, MAIN, SIDE, SALAD, SNACK, BAKING, DESSERT, DRINK, SAUCE;
 val wire: String get() = pluginI18n.tr("legacy.RecipeCategory.$name").get("ru")
 companion object { fun parse(value: String?): RecipeCategory? = entries.firstOrNull { it.name == value || it.wire == value } }
}
internal enum class RecipeFilter { ALL }
