package dev.akkraizen.berill.plugins.nutrition.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.toDateOrNull
import kotlinx.coroutines.CancellationException
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.layout.*
import dev.akkraizen.berill.api.ui.state.*
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.nutrition.data.*
import dev.akkraizen.berill.plugins.nutrition.i18n.NutritionText
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
internal enum class FoodInput { SEARCH, BARCODE, NAME, KCAL, PROTEIN, FAT, CARBS, GRAMS }
@Serializable
internal data class QuickFoodDraft(val date: String, val meal: String, val pending: FoodEntry? = null, val inputs: Map<FoodInput, String> = emptyMap())
@Serializable
internal data class QuickMealTemplate(val meal: String, val foods: List<FoodEntry>)
internal val LocalFoodDraft = staticCompositionLocalOf<QuickDraft<QuickFoodDraft>?> { null }

@Composable
internal fun rememberFoodField(input: FoodInput, legacyKey: String, initial: String = ""): MutableState<String> {
  val draft = LocalFoodDraft.current
  val legacy = rememberDraft(legacyKey, initial)
  return remember(draft, input, initial) {
    if (draft == null) legacy else object : MutableState<String> {
      override var value: String
        get() = draft.value.inputs[input] ?: initial
        set(value) { draft.value = draft.value.copy(inputs = draft.value.inputs + (input to value)) }
      override fun component1() = value
      override fun component2(): (String) -> Unit = { value = it }
    }
  }
}

@OptIn(ExperimentalTime::class)
@Composable
internal fun QuickFoodDialog(ctx: PluginContext, onDismiss: () -> Unit, onError: (Exception) -> Unit) {
  val entries = LocalQuickEntries.current
  val initial = remember { QuickFoodDraft(today().toString(), mealByHour(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour)) }
  val draft = rememberQuickDraft(QuickEntryKind.FOOD, QuickFoodDraft.serializer(), initial)
  val value = draft.value
  val collection = remember(ctx) { foods(ctx) }
  val recent by collection.observe().collectAsState(emptyList())
  var repeatMeal by remember { mutableStateOf<QuickMealTemplate?>(null) }
  val popup = rememberPopupContext(ctx, onDismiss)
  QuickActionDialog(NutritionText.quickFood(), Icons.Outlined.Restaurant, onDismiss) {
    CompositionLocalProvider(LocalFoodDraft provides draft) {
      AddFoodScreen(popup, value.date, value.meal, onError, header = {
        QuickDateField(value.date) { draft.value = draft.value.copy(date = it) }
        val mealFoods = value.pending?.let { listOf(it) } ?: recent.map { it.value }.filter { it.date == value.date && it.meal == value.meal }
        val current = QuickMealTemplate(value.meal, mealFoods.map { it.copy(date = "", createdAt = 0) })
        val meals = recent.map { it.value }.sortedByDescending { it.createdAt }.groupBy { it.date to it.meal }.map { (_, foods) ->
          QuickMealTemplate(foods.first().meal, foods.map { it.copy(date = "", createdAt = 0) })
        }
        QuickPresets(QuickEntryKind.FOOD_MEAL, QuickMealTemplate.serializer(), current, mealFoods.isNotEmpty(), meals,
          { "${NutritionText.mealLabel(it.meal)} · ${it.foods.joinToString { food -> food.name }}" }, { repeatMeal = it })
        QuickDraftActions { draft.reset(initial.copy(date = today().toString())) }
      }, onCreated = { product, meal, grams, again ->
        saveQuickEntry(entries, collection, ctx.data.newId(), product.toEntry(draft.value.date, meal, grams, nowMillis()))
        draft.reset(QuickFoodDraft(draft.value.date, meal))
        if (!again) onDismiss()
      })
    }
  }
  repeatMeal?.let { template ->
    AlertDialog(onDismissRequest = { repeatMeal = null }, title = { Text(I18n.text("quick.repeatMeal")) },
      text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("${value.date} · ${NutritionText.mealLabel(value.meal)}")
          template.foods.forEach { Text("${it.name} · ${formatNum(it.grams)} ${NutritionText.grams()}") }
        }
      }, confirmButton = {
        QuickSaveButtons(value.date.toDateOrNull() != null, { repeatMeal = null }) { again ->
          try {
            saveQuickBatch(entries, collection, template.foods.map { food -> ctx.data.newId() to food.copy(date = value.date.toDateOrNull().toString(), meal = value.meal, createdAt = nowMillis()) })
            repeatMeal = null
            draft.reset(QuickFoodDraft(value.date, value.meal))
            if (!again) onDismiss()
          } catch (e: CancellationException) { throw e }
            catch (e: Exception) { onError(e) }
        }
      })
  }

}
