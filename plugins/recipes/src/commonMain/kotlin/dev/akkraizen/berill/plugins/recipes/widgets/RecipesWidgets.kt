package dev.akkraizen.berill.plugins.recipes.widgets

import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.markdown.markdownPlainText
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetButton
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetSize
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.plugins.recipes.RecipesPlugin
import dev.akkraizen.berill.plugins.recipes.data.Recipe
import dev.akkraizen.berill.plugins.recipes.data.recipes
import dev.akkraizen.berill.plugins.recipes.i18n.RecipesText

internal fun recipesWidgets(ctx: PluginContext): List<WidgetProvider> = listOf(RecipeOfDayWidget(ctx))

internal fun recipeOfDayIndex(size: Int, dayIso: String, shift: Int): Int {
  if (size == 0) return 0
  var acc = 11L
  for (ch in dayIso) acc = (acc * 31 + ch.code) and 0xFFFFFFFFL
  return ((acc + shift).mod(size.toLong())).toInt()
}

private class RecipeOfDayWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "day"
  override val title: Tr = RecipesText.widgetDayTitle
  override val description: Tr = RecipesText.widgetDayDescription
  override val size = WidgetSize.Medium

  private fun pool(): List<Item<Recipe>> {
    val all = recipes(ctx).all().filter { it.value.title.isNotBlank() }.sortedBy { it.id }
    return all.filter { it.value.favorite }.ifEmpty { all }
  }

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val pool = pool()
    if (pool.isEmpty())
      return WidgetContent(title = title(), icon = WidgetIcon.Recipe, open = WidgetAction.Open(), empty = RecipesText.widgetNoRecipes())
    val day = today().toString()
    val shift = if (request.state[DAY] == day) request.state[SHIFT]?.toIntOrNull() ?: 0 else 0
    val item = pool[recipeOfDayIndex(pool.size, day, shift)]
    val recipe = item.value
    val meta = listOfNotNull(
      recipe.category?.let { RecipesText.categoryLabel(it) },
      recipe.time?.takeIf { it > 0 }?.let { "$it ${RecipesText.timeMin()}" },
      recipe.servings?.ifBlank { null }?.let { "${RecipesText.servings()}: $it" }
    ).joinToString(" · ").ifBlank { null }
    val preview = markdownPlainText(recipe.body.orEmpty()).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(PREVIEW_LINES).joinToString("\n")
    return WidgetContent(
      title = title(),
      subtitle = if (recipe.favorite) RecipesText.favorites() else null,
      icon = WidgetIcon.Recipe,
      open = WidgetAction.Open(route = RecipesPlugin.RECIPE_ROUTE + item.id),
      blocks = listOfNotNull(
        WidgetBlock.Text(recipe.title, large = true),
        meta?.let { WidgetBlock.Text(it, WidgetTone.Muted) },
        preview.ifBlank { null }?.let { WidgetBlock.Text(it) },
        WidgetBlock.Buttons(
          listOf(
            WidgetButton(RecipesText.widgetOpen(), WidgetAction.Open(route = RecipesPlugin.RECIPE_ROUTE + item.id), WidgetIcon.Open, primary = true),
            WidgetButton(RecipesText.widgetAnother(), WidgetAction.Run(NEXT), WidgetIcon.Refresh)
          )
        )
      )
    )
  }

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect {
    if (action.id != NEXT) return WidgetEffect.Refresh()
    val day = today().toString()
    val shift = if (request.state[DAY] == day) request.state[SHIFT]?.toIntOrNull() ?: 0 else 0
    return WidgetEffect.Refresh(state = mapOf(DAY to day, SHIFT to (shift + 1).toString()))
  }

  private companion object {
    const val NEXT = "next"
    const val DAY = "day"
    const val SHIFT = "shift"
    const val PREVIEW_LINES = 4
  }
}
