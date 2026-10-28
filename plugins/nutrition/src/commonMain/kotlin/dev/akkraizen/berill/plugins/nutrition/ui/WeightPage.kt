package dev.akkraizen.berill.plugins.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.minusDays
import dev.akkraizen.berill.api.ui.parseAmount
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.nutrition.data.WeightEntry
import dev.akkraizen.berill.plugins.nutrition.data.formatNum
import dev.akkraizen.berill.plugins.nutrition.data.weights
import dev.akkraizen.berill.plugins.nutrition.i18n.NutritionText

private val WEIGHT_RANGES = listOf<Int?>(30, 90, 365, null)

@Composable
internal fun WeightPage(ctx: PluginContext) {
  val collection = remember(ctx) { weights(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  val entries = all.mapNotNull { item -> item.value.date.toDateOrNull()?.let { it to item.value.kg } }
    .sortedBy { it.first }
  var range by rememberSaveable { mutableStateOf<Int?>(90) }

  PluginList {
    item { WeighInCard(ctx) }
    if (entries.isNotEmpty())
      item {
        SectionCard(NutritionText.history(), Icons.Outlined.History) {
          val desc = entries.reversed()
          desc.take(60).forEachIndexed { i, (date, kg) ->
            val prev = desc.getOrNull(i + 1)?.second
            Row(
              Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                date.dayMonth() + if (date.year != today().year) " ${date.year}" else "",
                modifier = Modifier.weight(1f)
              )
              if (prev != null) {
                val d = kg - prev
                Text(
                  (if (d > 0) "+" else "") + formatNum(d),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Text(NutritionText.kgValue(formatNum(kg))(), style = MaterialTheme.typography.titleSmall)
              IconButton(onClick = { collection.delete(date.toString()) }) {
                Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    item {
      SectionCard(NutritionText.chart(), Icons.Outlined.ShowChart) {
        ChoiceChips(WEIGHT_RANGES, range, { rangeLabel(it) }, { range = it })
        val from = range?.let { today().minusDays(it) }
        val shown = if (from == null) entries else entries.filter { it.first >= from }
        if (shown.isEmpty()) {
          EmptyHint(
            if (entries.isEmpty())
              NutritionText.noWeights()
            else
              NutritionText.noStats()
          )
        } else {
          LineChart(shown, { NutritionText.kgValue(formatNum(it))() })
        }
      }
    }
  }
}

private fun rangeLabel(days: Int?): String =
  when (days) {
    30 -> NutritionText.range30()
    90 -> NutritionText.range90()
    365 -> NutritionText.range365()
    else -> NutritionText.rangeAll()
  }

@Composable
private fun WeighInCard(ctx: PluginContext) {
  val collection = remember(ctx) { weights(ctx) }
  var value by rememberDraft("nutrition/weight/value")
  var date by rememberSaveable { mutableStateOf<String?>(null) }
  val kg = parseAmount(value)?.takeIf { it in 20.0..400.0 }
  fun submit() {
    if (kg == null) return
    val day = date?.toDateOrNull()?.toString() ?: today().toString()
    collection.save(day, WeightEntry(day, kg))
    value = ""
  }
  SectionCard(NutritionText.weighIn(), Icons.Outlined.MonitorWeight) {
    FieldsRow(minItemWidth = 160.dp) {
      BerillTextField(
        value,
        { value = it.take(6) },
        label = { Text(NutritionText.weightKg(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = value.isNotBlank() && kg == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        onSubmit = ::submit
      )
      DateField(date ?: today().toString(), { date = it }, NutritionText.date())
    }
    FilledTonalButton(onClick = ::submit, enabled = kg != null) { Text(NutritionText.save()) }
  }
}
