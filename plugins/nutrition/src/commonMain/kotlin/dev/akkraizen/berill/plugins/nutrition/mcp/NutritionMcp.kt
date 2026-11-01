package dev.akkraizen.berill.plugins.nutrition.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.nutrition.NutritionPlugin
import dev.akkraizen.berill.plugins.nutrition.data.*

internal class NutritionMcp(ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, NutritionPlugin.FOODS, FoodEntry.serializer(), normalize = { old, next -> next.copy(date = next.date.toDateOrNull()?.toString() ?: next.date, createdAt = old?.createdAt ?: nowMillis()) }, validate = { require(it.name.isNotBlank() && it.date.toDateOrNull() != null && it.grams.isFinite() && it.grams > 0 && it.kcal100.isFinite() && it.kcal100 >= 0) { "Food name, valid date, portion and calories required" } }),
    TypedMcpCollection(ctx, NutritionPlugin.WEIGHTS, WeightEntry.serializer(), createId = { it.date }, normalize = { old, next -> next.copy(date = next.date.toDateOrNull()?.toString() ?: next.date, source = null).also { require(old == null || old.date == it.date) { "Weight date is immutable" } } }, validate = { require(it.date.toDateOrNull() != null && it.kg.isFinite() && it.kg > 0) { "Valid date and positive weight required" } }),
    TypedMcpCollection(ctx, NutritionPlugin.WATER, WaterDay.serializer(), createId = { it.date }, normalize = { old, next -> next.copy(date = next.date.toDateOrNull()?.toString() ?: next.date).also { require(old == null || old.date == it.date) { "Water date is immutable" } } }, validate = { require(it.date.toDateOrNull() != null && it.ml >= 0) { "Valid date and nonnegative water required" } })
  )
  override val collections: List<McpCollection> = editable + (NutritionPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}
