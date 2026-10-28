package dev.akkraizen.berill.plugins.nutrition.widgets

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.ddMM
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetButton
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetMetric
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetSize
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.api.widgets.widgetFraction
import dev.akkraizen.berill.plugins.nutrition.NutritionPlugin
import dev.akkraizen.berill.plugins.nutrition.data.WaterDay
import dev.akkraizen.berill.plugins.nutrition.data.foods
import dev.akkraizen.berill.plugins.nutrition.data.formatNum
import dev.akkraizen.berill.plugins.nutrition.data.kcalText
import dev.akkraizen.berill.plugins.nutrition.data.mealByHour
import dev.akkraizen.berill.plugins.nutrition.data.nutritionPrefs
import dev.akkraizen.berill.plugins.nutrition.data.totals
import dev.akkraizen.berill.plugins.nutrition.data.water
import dev.akkraizen.berill.plugins.nutrition.data.weights
import dev.akkraizen.berill.plugins.nutrition.i18n.NutritionText
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal fun nutritionWidgets(ctx: PluginContext): List<WidgetProvider> =
  listOf(CaloriesWidget(ctx), WaterWidget(ctx), WeightWidget(ctx))

private fun addFoodAction(nowMillis: Long): WidgetAction {
  val now = if (nowMillis > 0) Instant.fromEpochMilliseconds(nowMillis) else Clock.System.now()
  val hour = now.toLocalDateTime(TimeZone.currentSystemDefault()).hour
  return WidgetAction.Open(route = "${NutritionPlugin.ADD_ROUTE}${today()}:${mealByHour(hour)}", page = NutritionPlugin.PAGE_DIARY)
}

private fun liters(ml: Int) = NutritionText.litersValue(formatNum(ml / 1000.0))()

private class CaloriesWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "calories"
  override val title: Tr = NutritionText.widgetCaloriesTitle
  override val description: Tr = NutritionText.widgetCaloriesDescription
  override val size = WidgetSize.Medium

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val norm = nutritionPrefs(ctx).norm
    val day = today().toString()
    val totals = foods(ctx).all().map { it.value }.filter { it.date == day }.totals()
    val left = norm.kcal - totals.kcal
    return WidgetContent(
      title = NutritionText.title(),
      subtitle = NutritionText.today(),
      icon = WidgetIcon.Food,
      open = WidgetAction.Open(page = NutritionPlugin.PAGE_DIARY),
      buttons = listOf(WidgetButton(NutritionText.add(), addFoodAction(request.nowMillis), WidgetIcon.Add)),
      blocks = listOf(
        WidgetBlock.Ring(
          WidgetMetric(
            label = NutritionText.kcal(),
            value = totals.kcal.kcalText(),
            subtitle = if (left >= 0) "${NutritionText.left()}: ${left.kcalText()}" else "${NutritionText.over()}: ${(-left).kcalText()}",
            progress = widgetFraction(totals.kcal, norm.kcal.toDouble()),
            tone = if (left < 0) WidgetTone.Warn else WidgetTone.Accent
          )
        ),
        WidgetBlock.Bar(NutritionText.proteinShort(), "${totals.protein.roundToInt()}/${norm.protein}", widgetFraction(totals.protein, norm.protein.toDouble()), WidgetTone.Good),
        WidgetBlock.Bar(NutritionText.fatShort(), "${totals.fat.roundToInt()}/${norm.fat}", widgetFraction(totals.fat, norm.fat.toDouble()), WidgetTone.Warn),
        WidgetBlock.Bar(NutritionText.carbsShort(), "${totals.carbs.roundToInt()}/${norm.carbs}", widgetFraction(totals.carbs, norm.carbs.toDouble()), WidgetTone.Accent)
      )
    )
  }

  override suspend fun summary(request: WidgetRequest): WidgetRow {
    val norm = nutritionPrefs(ctx).norm
    val day = today().toString()
    val eaten = foods(ctx).all().map { it.value }.filter { it.date == day }.totals().kcal
    val waterMl = water(ctx).get(day)?.ml ?: 0
    return WidgetRow(
      title = NutritionText.title(),
      subtitle = "${NutritionText.water()}: ${liters(waterMl)} / ${liters(norm.waterMl)}",
      trailing = "${eaten.kcalText()} / ${norm.kcal}",
      action = WidgetAction.Open(pluginId = NutritionPlugin.id, page = NutritionPlugin.PAGE_DIARY),
      tone = if (eaten > norm.kcal) WidgetTone.Warn else WidgetTone.Normal,
      icon = WidgetIcon.Food
    )
  }
}

private class WaterWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "water"
  override val title: Tr = NutritionText.widgetWaterTitle
  override val description: Tr = NutritionText.widgetWaterDescription
  override val size = WidgetSize.Small

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val prefs = nutritionPrefs(ctx)
    val day = today().toString()
    val ml = water(ctx).get(day)?.ml ?: 0
    val glass = prefs.glassMl
    return WidgetContent(
      title = NutritionText.water(),
      subtitle = "${liters(ml)} / ${liters(prefs.norm.waterMl)}",
      icon = WidgetIcon.Water,
      open = WidgetAction.Open(page = NutritionPlugin.PAGE_DIARY),
      blocks = listOf(
        WidgetBlock.Bar(NutritionText.today(), NutritionText.mlValue(ml.toString())(), widgetFraction(ml.toDouble(), prefs.norm.waterMl.toDouble()), WidgetTone.Accent),
        WidgetBlock.Buttons(
          listOfNotNull(
            WidgetButton("+$glass", WidgetAction.Run(CHANGE, mapOf(DELTA to glass.toString())), WidgetIcon.Water, primary = true),
            WidgetButton("+${glass * 2}", WidgetAction.Run(CHANGE, mapOf(DELTA to (glass * 2).toString())), WidgetIcon.Water),
            if (ml > 0) WidgetButton("−$glass", WidgetAction.Run(CHANGE, mapOf(DELTA to (-glass).toString())), WidgetIcon.Remove) else null
          )
        )
      )
    )
  }

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect {
    if (action.id != CHANGE) return WidgetEffect.Refresh()
    val delta = action.args[DELTA]?.toIntOrNull() ?: return WidgetEffect.Refresh()
    val day = today().toString()
    val collection = water(ctx)
    val next = ((collection.get(day)?.ml ?: 0) + delta).coerceAtLeast(0)
    collection.save(day, WaterDay(day, next))
    return WidgetEffect.Refresh()
  }

  private companion object {
    const val CHANGE = "change"
    const val DELTA = "delta"
  }
}

private class WeightWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "weight"
  override val title: Tr = NutritionText.widgetWeightTitle
  override val description: Tr = NutritionText.widgetWeightDescription
  override val size = WidgetSize.Medium

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val entries = weights(ctx).all().map { it.value }.filter { it.date.toDateOrNull() != null }.sortedBy { it.date }.takeLast(POINTS)
    val latest = entries.lastOrNull()
    val first = entries.firstOrNull()
    val delta = if (latest != null && first != null && entries.size > 1) latest.kg - first.kg else null
    return WidgetContent(
      title = NutritionText.pageWeight(),
      subtitle = latest?.let { NutritionText.kgValue(formatNum(it.kg))() },
      icon = WidgetIcon.Fitness,
      open = WidgetAction.Open(page = NutritionPlugin.PAGE_WEIGHT),
      blocks = if (entries.size < 2)
        listOfNotNull(latest?.let { WidgetBlock.Text(NutritionText.kgValue(formatNum(it.kg))(), large = true) })
      else
        listOfNotNull(
          WidgetBlock.Sparkline(entries.map { it.kg }, first?.date?.toDateOrNull()?.ddMM(), latest?.date?.toDateOrNull()?.ddMM()),
          delta?.let {
            WidgetBlock.Text(
              NutritionText.widgetWeightDelta((if (it > 0) "+" else if (it < 0) "−" else "") + formatNum(kotlin.math.abs(it)), entries.size)(),
              WidgetTone.Muted
            )
          }
        ),
      empty = NutritionText.widgetNoWeight()
    )
  }

  private companion object {
    const val POINTS = 30
  }
}
