package dev.akkraizen.berill.plugins.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.History
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
import dev.akkraizen.berill.api.ui.charts.StatisticsPeriod
import dev.akkraizen.berill.api.ui.charts.recentDates
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.fields.TimeField
import dev.akkraizen.berill.api.ui.fields.parseClock
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.health.data.SleepEntry
import dev.akkraizen.berill.plugins.health.data.activity
import dev.akkraizen.berill.plugins.health.data.sleepLog
import dev.akkraizen.berill.plugins.health.i18n.HealthText

@Composable
internal fun SleepPage(ctx: PluginContext) {
  val log = remember(ctx) { sleepLog(ctx) }
  val entries by remember(log) { log.observe() }.collectAsState(emptyList())
  val imported by remember(ctx) { activity(ctx).observe() }.collectAsState(emptyList())
  var period by rememberSaveable { mutableStateOf(30) }

  PluginList {
    item { SleepForm(ctx) }
    item {
      SectionCard(HealthText.sleepChart(), Icons.Outlined.Bedtime) {
        StatisticsPeriod(period) { period = it }
        val dates = recentDates(period)
        val manual = entries.associate { it.id to it.value.minutes() }
        val synced = imported.associate { it.id to it.value.sleepMinutes }
        val hours = dates.map { date -> (synced[date.toString()]?.toInt() ?: manual[date.toString()])?.let { it / 60.0 } }
        StatisticsChart(dates.map { it.dayMonthShort() }, listOf(ChartSeries(HealthText.sleepHours(), hours)), kind = ChartKind.Bars, target = 8.0,
          valueLabel = { formatDuration((it * 60).toInt()) })
      }
    }
    item {
      SectionCard(HealthText.steps(), Icons.Outlined.DirectionsWalk) {
        val dates = recentDates(period)
        val steps = imported.associate { it.id to it.value.steps }
        val values = dates.map { steps[it.toString()]?.toDouble() }
        if (values.all { it == null }) EmptyHint(HealthText.noSteps())
        else StatisticsChart(dates.map { it.dayMonthShort() }, listOf(ChartSeries(HealthText.steps(), values)), kind = ChartKind.Bars, valueLabel = { formatNumber(it) })
      }
    }
    val history = entries.sortedByDescending { it.id }.take(60)
    if (history.isNotEmpty())
      item {
        SectionCard(HealthText.history(), Icons.Outlined.History) {
          history.forEach { item ->
            val entry = item.value
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(item.id.toDateOrNull()?.dayMonth() ?: item.id, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(
                listOfNotNull("${entry.bedtime}–${entry.wake}", entry.quality?.let { HealthText.quality() + " $it/5" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(entry.minutes()?.let(::formatDuration) ?: "", style = MaterialTheme.typography.titleSmall)
              IconButton(onClick = { log.delete(item.id) }) { Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
          }
        }
      }
  }
}

@Composable
private fun SleepForm(ctx: PluginContext) {
  var date by rememberSaveable { mutableStateOf(today().toString()) }
  var bedtime by rememberSaveable { mutableStateOf("23:00") }
  var wake by rememberSaveable { mutableStateOf("07:00") }
  var quality by rememberSaveable { mutableStateOf<Int?>(null) }
  val entry = SleepEntry(date, bedtime.trim(), wake.trim(), quality)
  val problem = entry.problem()
  SectionCard(HealthText.logSleep(), Icons.Outlined.Bedtime) {
    Text(HealthText.sleepHint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    DateField(date, { picked -> picked?.toDateOrNull()?.let { date = it.toString() } }, HealthText.wakeDate())
    FieldsRow(minItemWidth = 140.dp) {
      TimeField(bedtime, { bedtime = it }, HealthText.bedtime())
      TimeField(wake, { wake = it }, HealthText.wakeTime())
    }
    RatingRow(HealthText.quality(), quality) { quality = it }
    entry.minutes()?.let { Text(HealthText.sleptValue(formatDuration(it))(), style = MaterialTheme.typography.labelLarge) }
    FilledTonalButton(enabled = problem == null, onClick = {
      sleepLog(ctx).save(date, entry)
      quality = null
    }) { Text(HealthText.save()) }
  }
}
