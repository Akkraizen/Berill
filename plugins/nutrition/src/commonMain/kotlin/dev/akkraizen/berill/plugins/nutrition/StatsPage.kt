package dev.akkraizen.berill.plugins.nutrition

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.StatisticsPeriod
import dev.akkraizen.berill.api.ui.charts.recentDates
import dev.akkraizen.berill.api.ui.layout.PluginList

@Composable
internal fun StatsPage(ctx: PluginContext) {
  val prefs = rememberNutritionPrefs(ctx)
  val foodCollection = remember(ctx) { foods(ctx) }
  val allFood by remember(foodCollection) { foodCollection.observe() }.collectAsState(emptyList())
  val waterCollection = remember(ctx) { water(ctx) }
  val allWater by remember(waterCollection) { waterCollection.observe() }.collectAsState(emptyList())
  var range by rememberSaveable { mutableIntStateOf(7) }
  val days = recentDates(range)
  val keys = days.map { it.toString() }.toSet()
  val byDay = allFood.map { it.value }.filter { it.date in keys }.groupBy { it.date }.mapValues { it.value.totals() }
  PluginList {
    item { StatisticsPeriod(range) { range = it } }
    item {
      SectionCard(NutritionText.kcalByDay(), Icons.Outlined.BarChart) {
        BarChart(days, days.map { byDay[it.toString()]?.kcal }, prefs.norm.kcal.toDouble())
      }
    }
    item { NutritionHistoryCharts(days, byDay, allWater.map { it.value }, prefs.norm) }
  }
}
