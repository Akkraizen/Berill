package dev.akkraizen.berill.plugins.nutrition.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.minusDays
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.nutrition.NutritionPlugin
import dev.akkraizen.berill.plugins.nutrition.data.FoodEntry
import dev.akkraizen.berill.plugins.nutrition.data.MEALS
import dev.akkraizen.berill.plugins.nutrition.data.foods
import dev.akkraizen.berill.plugins.nutrition.data.rememberNutritionPrefs
import dev.akkraizen.berill.plugins.nutrition.data.totals
import dev.akkraizen.berill.plugins.nutrition.i18n.NutritionText
import kotlinx.datetime.LocalDate

internal object DiaryDay {
  var selected by mutableStateOf<LocalDate?>(null)
  val date: LocalDate
    get() = selected ?: today()
}

internal fun LocalDate.dayTitle(): String =
  when (this) {
    today() -> NutritionText.today()
    today().minusDays(1) -> NutritionText.yesterday()
    else -> dayMonth()
  }

@Composable
internal fun DiaryPage(ctx: PluginContext) {
  val route by ctx.nav.route.collectAsState()
  AnimatedRoute(route) { r ->
    val add = r?.takeIf { it.startsWith(NutritionPlugin.ADD_ROUTE) }
      ?.removePrefix(NutritionPlugin.ADD_ROUTE)
      ?.split(':')
    if (add != null && add.size == 2) {
      AddFoodScreen(ctx, add[0], add[1])
    } else {
      DiaryHome(ctx)
    }
  }
}

@Composable
private fun DiaryHome(ctx: PluginContext) {
  val prefs = rememberNutritionPrefs(ctx)
  val date = DiaryDay.date
  val day = date.toString()
  val collection = remember(ctx) { foods(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  val dayItems = all.filter { it.value.date == day }.sortedBy { it.value.createdAt }
  val totals = dayItems.map { it.value }.totals()
  var editing by remember { mutableStateOf<Item<FoodEntry>?>(null) }

  PluginList {
    item { DaySwitcher(date) }
    item {
      TwoPane(
        left = {
          SummaryCard(totals, prefs.norm)
          WaterCard(ctx, day, prefs)
        },
        right = {
          MEALS.forEach { meal ->
            MealCard(
              meal = meal,
              items = dayItems.filter { it.value.meal == meal },
              onAdd = { openAdd(ctx, day, meal) },
              onEdit = { editing = it }
            )
          }
        },
        leftWeight = 1f
      )
    }
  }

  editing?.let { item ->
    EditEntryDialog(
      entry = item.value,
      onDismiss = { editing = null },
      onSave = { grams, meal ->
        collection.get(item.id)?.let { fresh ->
          collection.save(item.id, fresh.copy(grams = grams, meal = meal))
        }
        editing = null
      },
      onDelete = {
        collection.delete(item.id)
        editing = null
      }
    )
  }
}

@Composable
private fun DaySwitcher(date: LocalDate) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = { DiaryDay.selected = date.minusDays(1) }) {
      Icon(BerillIcons.Prev, contentDescription = NutritionText.previousDay())
    }
    TextButton(onClick = { DiaryDay.selected = null }) {
      Text(date.dayTitle(), style = MaterialTheme.typography.titleLarge)
    }
    IconButton(onClick = { DiaryDay.selected = date.plusDays(1) }, enabled = date < today()) {
      Icon(BerillIcons.Next, contentDescription = NutritionText.nextDay())
    }
  }
}
