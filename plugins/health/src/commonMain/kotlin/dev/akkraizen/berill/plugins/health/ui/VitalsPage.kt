package dev.akkraizen.berill.plugins.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
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
import dev.akkraizen.berill.api.ui.charts.ChartKind
import dev.akkraizen.berill.api.ui.charts.ChartSeries
import dev.akkraizen.berill.api.ui.charts.StatisticsChart
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.fields.TimeField
import dev.akkraizen.berill.api.ui.fields.clockText
import dev.akkraizen.berill.api.ui.fields.parseClock
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.layout.TAB_STATISTICS
import dev.akkraizen.berill.api.ui.layout.TabbedPluginList
import dev.akkraizen.berill.api.ui.localDateTime
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.parseAmount
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.health.data.VitalEntry
import dev.akkraizen.berill.plugins.health.data.VitalType
import dev.akkraizen.berill.plugins.health.data.vitals
import dev.akkraizen.berill.plugins.health.i18n.HealthText

@Composable
internal fun VitalsPage(ctx: PluginContext) {
  val collection = remember(ctx) { vitals(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  var type by rememberSaveable { mutableStateOf(VitalType.PULSE) }
  val entries = all.filter { it.value.type == type }
    .sortedWith(compareByDescending<dev.akkraizen.berill.api.Item<VitalEntry>> { it.value.date }.thenByDescending { it.value.time ?: "" })

  TabbedPluginList { tab ->
    item { ChoiceChips(VitalType.entries, type, ::vitalLabel, { type = it }) }
    if (tab == TAB_STATISTICS) {
      item {
        SectionCard(HealthText.chart(), Icons.Outlined.ShowChart) {
          val points = entries.reversed().takeLast(30)
          if (points.isEmpty()) EmptyHint(HealthText.noVitals())
          else {
            val labels = points.map { item -> item.value.date.toDateOrNull()?.dayMonthShort() ?: item.value.date }
            val series = buildList {
              add(ChartSeries(vitalLabel(type), points.map { it.value.value }))
              if (type.paired) add(ChartSeries(HealthText.diastolic(), points.map { it.value.value2 }))
            }
            StatisticsChart(labels, series, kind = ChartKind.Line, valueLabel = { "${formatNumber(it)} ${vitalUnit(type)}" })
          }
        }
      }
      return@TabbedPluginList
    }
    item {
      SectionCard(HealthText.measure(), Icons.Outlined.MonitorHeart) { MeasureForm(ctx, type) }
    }
    if (entries.isNotEmpty())
      item {
        SectionCard(HealthText.history(), Icons.Outlined.History) {
          entries.take(60).forEach { item ->
            val entry = item.value
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              val day = entry.date.toDateOrNull()?.let { if (it.year == today().year) it.dayMonth() else "${it.dayMonth()} ${it.year}" } ?: entry.date
              Text(listOfNotNull(day, entry.time).joinToString(" "), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
              entry.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
              }
              Text(vitalValue(entry), style = MaterialTheme.typography.titleSmall)
              IconButton(onClick = { collection.delete(item.id) }) { Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
          }
        }
      }
  }
}

@Composable
private fun MeasureForm(ctx: PluginContext, type: VitalType) {
  val draft = remember(type) { VitalDraft(type) }
  val problem = draft.entry().problem()
  fun submit() {
    if (problem != null) return
    vitals(ctx).save(ctx.data.newId(), draft.entry().copy(createdAt = nowMillis()))
    draft.clear()
  }
  VitalInputs(draft, ::submit)
  FilledTonalButton(onClick = ::submit, enabled = problem == null) { Text(HealthText.save()) }
}

internal class VitalDraft(type: VitalType) {
  var type by mutableStateOf(type)
  var first by mutableStateOf("")
  var second by mutableStateOf("")
  var note by mutableStateOf("")
  var date by mutableStateOf(today().toString())
  var time by mutableStateOf(localDateTime(nowMillis()).time.clockText())

  fun entry() = VitalEntry(
    type = type,
    date = date.toDateOrNull()?.toString() ?: today().toString(),
    time = parseClock(time)?.clockText(),
    value = parseAmount(first) ?: Double.NaN,
    value2 = if (type.paired) parseAmount(second) ?: Double.NaN else null,
    note = note.trim().ifEmpty { null }
  )

  fun clear() {
    first = ""
    second = ""
    note = ""
  }
}

@Composable
internal fun VitalInputs(draft: VitalDraft, onSubmit: () -> Unit) {
  val type = draft.type
  val problem = draft.entry().problem()
  FieldsRow(minItemWidth = 140.dp) {
    BerillTextField(
      draft.first, { draft.first = it.take(6) },
      label = { Text(if (type.paired) HealthText.systolic() else vitalUnit(type), maxLines = 1, overflow = TextOverflow.Ellipsis) },
      singleLine = true,
      isError = draft.first.isNotBlank() && problem != null && (parseAmount(draft.first)?.let { it !in type.min..type.max } ?: true),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier = Modifier.fillMaxWidth(),
      onSubmit = onSubmit
    )
    if (type.paired)
      BerillTextField(
        draft.second, { draft.second = it.take(6) },
        label = { Text(HealthText.diastolic(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = draft.second.isNotBlank() && problem != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        onSubmit = onSubmit
      )
  }
  FieldsRow(minItemWidth = 140.dp) {
    DateField(draft.date, { picked -> draft.date = picked?.toDateOrNull()?.toString() ?: today().toString() }, HealthText.date())
    TimeField(draft.time, { draft.time = it }, HealthText.time())
  }
  BerillTextField(draft.note, { draft.note = it.take(200) }, label = { Text(HealthText.note()) }, singleLine = true, modifier = Modifier.fillMaxWidth(), onSubmit = onSubmit)
}
