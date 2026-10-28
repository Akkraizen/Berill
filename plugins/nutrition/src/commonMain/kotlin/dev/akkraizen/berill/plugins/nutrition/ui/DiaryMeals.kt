package dev.akkraizen.berill.plugins.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.parseAmount
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.nutrition.data.FoodEntry
import dev.akkraizen.berill.plugins.nutrition.data.MEALS
import dev.akkraizen.berill.plugins.nutrition.data.NutritionPrefs
import dev.akkraizen.berill.plugins.nutrition.data.WaterDay
import dev.akkraizen.berill.plugins.nutrition.data.formatNum
import dev.akkraizen.berill.plugins.nutrition.data.kcalText
import dev.akkraizen.berill.plugins.nutrition.data.totals
import dev.akkraizen.berill.plugins.nutrition.data.water
import dev.akkraizen.berill.plugins.nutrition.i18n.NutritionText

@Composable
internal fun MealCard(meal: String, items: List<Item<FoodEntry>>, onAdd: () -> Unit, onEdit: (Item<FoodEntry>) -> Unit) {
  val totals = items.map { it.value }.totals()
  SectionCard(
    NutritionText.mealLabel(meal),
    trailing = {
      if (items.isNotEmpty()) {
        Text(
          NutritionText.kcalValue(totals.kcal.kcalText())(),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
      FilledTonalIconButton(onClick = onAdd) {
        Icon(BerillIcons.Add, contentDescription = NutritionText.addTo(NutritionText.mealLabel(meal))())
      }
    }
  ) {
    if (items.isEmpty()) {
      Text(
        NutritionText.noFood(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    items.forEach { item -> EntryRow(item.value) { onEdit(item) } }
  }
}

@Composable
private fun EntryRow(e: FoodEntry, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth()
      .interactive(MaterialTheme.shapes.small, bleed = 6.dp, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Column(Modifier.weight(1f)) {
      Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        listOfNotNull(NutritionText.gramsValue(formatNum(e.grams))(), e.brand, macroText(e.protein, e.fat, e.carbs))
          .joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Text(e.kcal.kcalText(), style = MaterialTheme.typography.titleSmall)
  }
}

@Composable
internal fun EditEntryDialog(
  entry: FoodEntry,
  onDismiss: () -> Unit,
  onSave: (Double, String) -> Unit,
  onDelete: () -> Unit
) {
  var grams by remember { mutableStateOf(formatNum(entry.grams)) }
  var meal by remember { mutableStateOf(entry.meal) }
  val g = parseAmount(grams)?.takeIf { it > 0 }
  val confirm: () -> Unit = { if (g != null) onSave(g, meal) }
  DialogKeys(onConfirm = confirm, confirmEnabled = g != null, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BerillTextField(
          grams,
          { grams = it.take(7) },
          label = { Text(NutritionText.grams(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true,
          isError = g == null,
          modifier = Modifier.fillMaxWidth()
        )
        val portion = g ?: 0.0
        Text(
          "${NutritionText.kcalValue((entry.kcal100 * portion / 100).kcalText())()} · " +
            macroText(entry.protein100 * portion / 100, entry.fat100 * portion / 100, entry.carbs100 * portion / 100),
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          NutritionText.meal(),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChoiceChips(MEALS, meal, { NutritionText.mealLabel(it) }, { meal = it })
      }
    },
    confirmButton = {
      TextButton(onClick = confirm, enabled = g != null) { Text(NutritionText.save()) }
    },
    dismissButton = {
      Row {
        TextButton(onClick = onDelete) {
          Text(NutritionText.delete(), color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onDismiss) { Text(NutritionText.cancel()) }
      }
    }
  )
}

@Composable
internal fun WaterCard(ctx: PluginContext, day: String, prefs: NutritionPrefs) {
  val collection = remember(ctx) { water(ctx) }
  val current by remember(collection, day) { collection.observe(day) }.collectAsState(null)
  val ml = current?.ml ?: 0
  val glass = prefs.glassMl
  fun change(delta: Int) {
    val fresh = collection.get(day)?.ml ?: 0
    val next = (fresh + delta).coerceAtLeast(0)
    collection.save(day, WaterDay(day, next))
  }
  SectionCard(NutritionText.water(), Icons.Outlined.WaterDrop) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedIconButton(onClick = { change(-glass) }, enabled = ml > 0) {
        Icon(Icons.Outlined.Remove, contentDescription = NutritionText.removeGlass())
      }
      Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          NutritionText.ofTarget(
            formatNum(ml / 1000.0, 2),
            NutritionText.litersValue(formatNum(prefs.norm.waterMl / 1000.0, 2))()
          )(),
          style = MaterialTheme.typography.titleMedium
        )
        Text(
          NutritionText.glasses(ml / glass) +
            " · " +
            NutritionText.mlValue(glass.toString())(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      FilledTonalIconButton(onClick = { change(glass) }) {
        Icon(BerillIcons.Add, contentDescription = NutritionText.addGlass())
      }
    }

    val total = (prefs.norm.waterMl + glass - 1) / glass
    val drunk = ml / glass
    Row(
      Modifier.fillMaxWidth().padding(top = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
      repeat(total.coerceIn(1, 16)) { i ->
        Ic(
          Icons.Outlined.WaterDrop,
          size = 18.dp,
          tint = if (i < drunk)
            MaterialTheme.colorScheme.primary
          else
            MaterialTheme.colorScheme.outlineVariant
        )
      }
    }
    ProgressLine(if (prefs.norm.waterMl > 0) ml.toFloat() / prefs.norm.waterMl else 0f)
  }
}
