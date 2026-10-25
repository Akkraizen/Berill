package dev.akkraizen.berill.plugins.nutrition

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPage
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.QuickAction
import dev.akkraizen.berill.api.ui.cards.Stat
import dev.akkraizen.berill.api.ui.cards.StatTiles
import dev.akkraizen.berill.api.ui.feedback.Tone
import dev.akkraizen.berill.api.ui.today
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object NutritionPlugin : BerillPlugin {
  const val FOODS = "food_entries"
  const val WEIGHTS = "body_weight"
  const val WATER = "water_days"

  const val PAGE_DIARY = "diary"
  const val PAGE_WEIGHT = "weight"
  const val PAGE_STATS = "stats"
  const val PAGE_NORM = "norm"

  internal const val ADD_ROUTE = "add:"

  override val id = "nutrition"
  override val title: String
    get() = NutritionText.title()

  override val icon = Icons.Outlined.LocalFireDepartment
  override val order = 13
  override val description: String
    get() = NutritionText.description()

  override val installedByDefault = true
  override val collections = listOf(FOODS, WEIGHTS, WATER)

  override val permissions = PluginPermissions(
    network = listOf(
      "world.openfoodfacts.org",
      "images.openfoodfacts.org",
      "static.openfoodfacts.org"
    )
  )
  override val hasSettings = true

  @Composable
  override fun Settings(ctx: PluginContext) = NormContent(ctx)

  override val pages = listOf(
    PluginPage(PAGE_DIARY, NutritionText.pageDiary, Icons.Outlined.Restaurant) { ctx ->
      DiaryPage(ctx)
    },
    PluginPage(PAGE_WEIGHT, NutritionText.pageWeight, Icons.Outlined.MonitorWeight) { ctx ->
      WeightPage(ctx)
    },
    PluginPage(PAGE_STATS, NutritionText.pageStats, Icons.Outlined.BarChart) { ctx ->
      StatsPage(ctx)
    },
    PluginPage(PAGE_NORM, NutritionText.pageNorm, Icons.Outlined.Tune) { ctx ->
      NormPage(ctx)
    }
  )

  override fun homeCards(ctx: PluginContext) =
    listOf(HomeCard("today", NutritionText.homeCard, order = 26) { NutritionHomeCard(ctx) })

  override fun quickActions(ctx: PluginContext) =
    listOf(
      QuickAction("food", NutritionText.quickFood, Icons.Outlined.Restaurant) {
        val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
        openAdd(ctx, today().toString(), mealByHour(hour))
      }
    )
}

internal fun openAdd(ctx: PluginContext, date: String, meal: String) {
  ctx.nav.openPlugin(
    NutritionPlugin.id,
    "${NutritionPlugin.ADD_ROUTE}$date:$meal",
    NutritionPlugin.PAGE_DIARY
  )
}

@Composable
private fun NutritionHomeCard(ctx: PluginContext) {
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
