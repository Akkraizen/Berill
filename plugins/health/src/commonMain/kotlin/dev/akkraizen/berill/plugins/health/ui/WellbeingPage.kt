package dev.akkraizen.berill.plugins.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Sick
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.ChartKind
import dev.akkraizen.berill.api.ui.charts.ChartSeries
import dev.akkraizen.berill.api.ui.charts.StatisticsChart
import dev.akkraizen.berill.api.ui.charts.recentDates
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.layout.TAB_STATISTICS
import dev.akkraizen.berill.api.ui.layout.TabbedPluginList
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.health.data.SEVERITIES
import dev.akkraizen.berill.plugins.health.data.Symptom
import dev.akkraizen.berill.plugins.health.data.WellbeingDay
import dev.akkraizen.berill.plugins.health.data.wellbeing
import dev.akkraizen.berill.plugins.health.i18n.HealthText

@Composable
internal fun WellbeingPage(ctx: PluginContext) {
  val collection = remember(ctx) { wellbeing(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  var date by rememberSaveable { mutableStateOf(today().toString()) }
  val stored = all.firstOrNull { it.id == date }?.value
  val current = stored ?: WellbeingDay(date)
  fun update(next: WellbeingDay) {
    if (next.isEmpty) collection.delete(date) else collection.save(date, next)
  }

  TabbedPluginList { tab ->
    if (tab == TAB_STATISTICS) {
      item {
        SectionCard(HealthText.chart(), Icons.Outlined.ShowChart) {
          val dates = recentDates(30)
          val byDate = all.associate { it.id to it.value }
          val days = dates.map { byDate[it.toString()] }
          if (days.all { it == null }) EmptyHint(HealthText.noWellbeing())
          else StatisticsChart(
            dates.map { it.dayMonthShort() },
            listOf(
              ChartSeries(HealthText.wellbeing(), days.map { it?.wellbeing?.toDouble() }),
              ChartSeries(HealthText.mood(), days.map { it?.mood?.toDouble() }),
              ChartSeries(HealthText.energy(), days.map { it?.energy?.toDouble() })
            ),
            kind = ChartKind.Line,
            target = 5.0,
            valueLabel = { formatNumber(it) }
          )
        }
      }
      return@TabbedPluginList
    }
    item {
      SectionCard(HealthText.howAreYou(), Icons.Outlined.SentimentSatisfied) {
        DateField(date, { picked -> picked?.toDateOrNull()?.let { date = it.toString() } }, HealthText.date())
        RatingRow(HealthText.wellbeing(), current.wellbeing) { update(current.copy(wellbeing = it)) }
        RatingRow(HealthText.mood(), current.mood) { update(current.copy(mood = it)) }
        RatingRow(HealthText.energy(), current.energy) { update(current.copy(energy = it)) }
        NoteField(date, current.note) { update(current.copy(note = it)) }
      }
    }
    item {
      SectionCard(HealthText.symptoms(), Icons.Outlined.Sick) {
        if (current.symptoms.isEmpty()) EmptyHint(HealthText.noSymptoms())
        current.symptoms.forEachIndexed { index, symptom ->
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(symptom.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
              ChoiceChips(SEVERITIES.toList(), symptom.severity, ::severityLabel, { severity ->
                update(current.copy(symptoms = current.symptoms.mapIndexed { i, s -> if (i == index) s.copy(severity = severity) else s }))
              })
            }
            IconButton(onClick = { update(current.copy(symptoms = current.symptoms.filterIndexed { i, _ -> i != index })) }) {
              Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
        AddSymptom(date) { name -> update(current.copy(symptoms = current.symptoms + Symptom(name, 1))) }
      }
    }
    val history = all.filter { it.id != date }.sortedByDescending { it.id }.take(30)
    if (history.isNotEmpty())
      item {
        SectionCard(HealthText.history(), Icons.Outlined.History) {
          history.forEach { item ->
            val day = item.id.toDateOrNull()
            Column(Modifier.fillMaxWidth()) {
              Text(day?.dayMonth() ?: item.id, style = MaterialTheme.typography.labelLarge)
              Text(
                listOfNotNull(
                  item.value.wellbeing?.let { HealthText.wellbeing() + " $it/5" },
                  item.value.mood?.let { HealthText.mood() + " $it/5" },
                  item.value.energy?.let { HealthText.energy() + " $it/5" },
                  item.value.symptoms.takeIf { it.isNotEmpty() }?.joinToString { "${it.name} (${severityLabel(it.severity)})" },
                  item.value.note?.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
  }
}

@Composable
private fun NoteField(date: String, note: String?, onCommit: (String?) -> Unit) {
  var text by remember(date, note) { mutableStateOf(note ?: "") }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    BerillTextField(text, { text = it.take(1000) }, label = { Text(HealthText.note()) }, modifier = Modifier.weight(1f))
    FilledTonalButton(onClick = { onCommit(text.trim().ifEmpty { null }) }, enabled = text.trim() != (note ?: "")) { Text(HealthText.save()) }
  }
}

@Composable
private fun AddSymptom(date: String, onAdd: (String) -> Unit) {
  var name by remember(date) { mutableStateOf("") }
  fun submit() {
    val value = name.trim()
    if (value.isEmpty()) return
    onAdd(value)
    name = ""
  }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    BerillTextField(name, { name = it.take(80) }, label = { Text(HealthText.symptomName()) }, singleLine = true, modifier = Modifier.weight(1f), onSubmit = ::submit)
    FilledTonalButton(onClick = ::submit, enabled = name.isNotBlank()) { Text(HealthText.add()) }
  }
}
