package dev.akkraizen.berill.plugins.nutrition.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.Stat
import dev.akkraizen.berill.api.ui.cards.StatTiles
import dev.akkraizen.berill.api.ui.feedback.Tone
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.nutrition.NutritionPlugin
import dev.akkraizen.berill.plugins.nutrition.data.foods
import dev.akkraizen.berill.plugins.nutrition.data.formatNum
import dev.akkraizen.berill.plugins.nutrition.data.kcalText
import dev.akkraizen.berill.plugins.nutrition.data.rememberNutritionPrefs
import dev.akkraizen.berill.plugins.nutrition.data.totals
import dev.akkraizen.berill.plugins.nutrition.data.water
import dev.akkraizen.berill.plugins.nutrition.i18n.NutritionText

internal fun openAdd(ctx: PluginContext, date: String, meal: String) {
  ctx.nav.openPlugin(
    NutritionPlugin.id,
    "${NutritionPlugin.ADD_ROUTE}$date:$meal",
    NutritionPlugin.PAGE_DIARY
  )
}

@Composable
internal fun NutritionHomeCard(ctx: PluginContext) {
  val prefs = rememberNutritionPrefs(ctx)
  val day = today().toString()
  val foodCollection = remember(ctx) { foods(ctx) }
  val all by remember(foodCollection) { foodCollection.observe() }.collectAsState(emptyList())
  val waterCollection = remember(ctx) { water(ctx) }
  val waterToday by
  remember(waterCollection, day) { waterCollection.observe(day) }.collectAsState(null)

  val totals = all.map { it.value }.filter { it.date == day }.totals()
  val left = prefs.norm.kcal - totals.kcal
  StatTiles(
    listOf(
      Stat(
        NutritionText.eaten(),
        NutritionText.kcalValue(totals.kcal.kcalText())(),
        sub = if (left >= 0)
          "${NutritionText.left()}: ${left.kcalText()}"
        else
          "${NutritionText.over()}: ${(-left).kcalText()}",
        tone = if (left < 0) Tone.Warn else Tone.Normal,
        icon = Icons.Outlined.LocalFireDepartment
      ),
      Stat(
        NutritionText.water(),
        NutritionText.litersValue(formatNum((waterToday?.ml ?: 0) / 1000.0))(),
        sub = NutritionText.litersValue(formatNum(prefs.norm.waterMl / 1000.0))(),
        icon = Icons.Outlined.WaterDrop
      )
    ),
    minTileWidth = 140.dp
  )
}
