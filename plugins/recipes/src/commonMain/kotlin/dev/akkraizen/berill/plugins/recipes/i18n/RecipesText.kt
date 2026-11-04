package dev.akkraizen.berill.plugins.recipes.i18n

import dev.akkraizen.berill.plugins.recipes.data.recipes

internal object RecipesText {

  val title = pluginI18n.tr("recipes.title")
  val view = pluginI18n.tr("recipes.view")
  val listView = pluginI18n.tr("recipes.listView")
  val description = pluginI18n.tr("recipes.description")

  private val categories = mapOf(
    dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.BREAKFAST to pluginI18n.tr("recipes.categories"),
    dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.SOUP to pluginI18n.tr("recipes.categories.2"),
    dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.MAIN to pluginI18n.tr("recipes.categories.3"),
    dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.SIDE to pluginI18n.tr("recipes.categories.4"),
    dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.SALAD to pluginI18n.tr("recipes.categories.5"),
    dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.SNACK to pluginI18n.tr("recipes.categories.6"),
    dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.BAKING to pluginI18n.tr("recipes.categories.7"),
    dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.DESSERT to pluginI18n.tr("recipes.categories.8"),
    dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.DRINK to pluginI18n.tr("recipes.categories.9"),
    dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.SAUCE to pluginI18n.tr("recipes.categories.10")
  )

  fun categoryLabel(category: String): String = categories[dev.akkraizen.berill.plugins.recipes.data.RecipeCategory.parse(category)]?.invoke() ?: category

  val searchPlaceholder = pluginI18n.tr("recipes.searchPlaceholder")
  val favorites = pluginI18n.tr("recipes.favorites")
  val recipe = pluginI18n.tr("recipes.recipe")
  val all = pluginI18n.tr("recipes.all")
  val nothingFound = pluginI18n.tr("recipes.nothingFound")
  val removeFromFavorites = pluginI18n.tr("recipes.removeFromFavorites")
  val addToFavorites = pluginI18n.tr("recipes.addToFavorites")
  val noCategory = pluginI18n.tr("recipes.noCategory")

  val newRecipe = pluginI18n.tr("recipes.newRecipe")
  val name = pluginI18n.tr("recipes.name")
  val category = pluginI18n.tr("recipes.category")
  val create = pluginI18n.tr("recipes.create")
  val cancel = pluginI18n.tr("recipes.cancel")

  fun bodyTemplate(): String = pluginI18n.text("recipes.bodyTemplate")

  val recipeDeleted = pluginI18n.tr("recipes.recipeDeleted")
  val untitled = pluginI18n.tr("recipes.untitled")
  val deleteWhat = pluginI18n.tr("recipes.deleteWhat")
  val bodyPlaceholder = pluginI18n.tr("recipes.bodyPlaceholder")
  val timeMin = pluginI18n.tr("recipes.timeMin")
  val servings = pluginI18n.tr("recipes.servings")
  val tagsCommaSeparated = pluginI18n.tr("recipes.tagsCommaSeparated")
  val photoLink = pluginI18n.tr("recipes.photoLink")

  val attachHint = pluginI18n.tr("recipes.attachHint")
  val makeCover = pluginI18n.tr("recipes.makeCover")
  val deletePhoto = pluginI18n.tr("recipes.deletePhoto")
  val close = pluginI18n.tr("recipes.close")

  val widgetDayTitle = pluginI18n.tr("recipes.widget.dayTitle")
  val widgetDayDescription = pluginI18n.tr("recipes.widget.dayDescription")
  val widgetNoRecipes = pluginI18n.tr("recipes.widget.noRecipes")
  val widgetOpen = pluginI18n.tr("recipes.widget.open")
  val widgetAnother = pluginI18n.tr("recipes.widget.another")
}
