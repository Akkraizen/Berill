package dev.akkraizen.berill.plugins.nutrition

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPage
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.QuickAction
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.nutrition.data.mealByHour
import dev.akkraizen.berill.plugins.nutrition.i18n.NutritionText
import dev.akkraizen.berill.plugins.nutrition.ui.BarChart
import dev.akkraizen.berill.plugins.nutrition.ui.DiaryPage
import dev.akkraizen.berill.plugins.nutrition.ui.NormContent
import dev.akkraizen.berill.plugins.nutrition.ui.NormPage
import dev.akkraizen.berill.plugins.nutrition.ui.NutritionHomeCard
import dev.akkraizen.berill.plugins.nutrition.ui.StatsPage
import dev.akkraizen.berill.plugins.nutrition.ui.WeightPage
import dev.akkraizen.berill.plugins.nutrition.ui.openAdd
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
