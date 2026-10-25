package dev.akkraizen.berill.plugins.recipes


internal object RecipesText {

  val title = pluginI18n.tr("recipes.title")
  val description = pluginI18n.tr("recipes.description")

  private val categories = mapOf(
    "Завтраки" to pluginI18n.tr("recipes.categories"),
    "Супы" to pluginI18n.tr("recipes.categories.2"),
    "Основные блюда" to pluginI18n.tr("recipes.categories.3"),
    "Гарниры" to pluginI18n.tr("recipes.categories.4"),
    "Салаты" to pluginI18n.tr("recipes.categories.5"),
    "Перекусы" to pluginI18n.tr("recipes.categories.6"),
    "Выпечка" to pluginI18n.tr("recipes.categories.7"),
    "Десерты" to pluginI18n.tr("recipes.categories.8"),
    "Напитки" to pluginI18n.tr("recipes.categories.9"),
    "Соусы" to pluginI18n.tr("recipes.categories.10")
  )

  fun categoryLabel(category: String): String = categories[category]?.invoke() ?: category

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
}
