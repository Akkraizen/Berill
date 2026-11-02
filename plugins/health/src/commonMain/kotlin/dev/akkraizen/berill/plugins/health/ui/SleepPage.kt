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
import dev.akkraizen.berill.api.ui.layout.TAB_STATISTICS
import dev.akkraizen.berill.api.ui.layout.TabbedPluginList
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.health.data.SleepEntry
import dev.akkraizen.berill.plugins.health.data.activity
import dev.akkraizen.berill.plugins.health.data.saveManualSteps
import dev.akkraizen.berill.plugins.health.data.rememberHealthPrefs
import dev.akkraizen.berill.plugins.health.data.sleepLog
import dev.akkraizen.berill.plugins.health.i18n.HealthText

@Composable
internal fun SleepPage(ctx: PluginContext) {
  val log = remember(ctx) { sleepLog(ctx) }
  val entries by remember(log) { log.observe() }.collectAsState(emptyList())
  val imported by remember(ctx) { activity(ctx).observe() }.collectAsState(emptyList())
  var period by rememberSaveable { mutableStateOf(30) }
  val prefs = rememberHealthPrefs(ctx)

  TabbedPluginList { tab ->
    if (tab == TAB_STATISTICS) {
      item { StatisticsPeriod(period) { period = it } }
      item {
        SectionCard(HealthText.sleepChart(), Icons.Outlined.Bedtime) {
          val dates = recentDates(period)
          val manual = entries.associate { it.id to it.value.minutes() }
          val synced = imported.associate { it.id to it.value.sleepMinutes }
          val hours = dates.map { date -> (synced[date.toString()]?.toInt() ?: manual[date.toString()])?.let { it / 60.0 } }
          StatisticsChart(dates.map { it.dayMonthShort() }, listOf(ChartSeries(HealthText.sleepHours(), hours)), kind = ChartKind.Bars, target = prefs.sleepGoalMinutes / 60.0,
            valueLabel = { formatDuration((it * 60).toInt()) })
        }
      }
      item {
        SectionCard(HealthText.steps(), Icons.Outlined.DirectionsWalk) {
          val dates = recentDates(period)
          val steps = imported.associate { it.id to it.value.steps }
          val values = dates.map { steps[it.toString()]?.toDouble() }
          if (values.all { it == null }) EmptyHint(HealthText.noSteps())
          else StatisticsChart(dates.map { it.dayMonthShort() }, listOf(ChartSeries(HealthText.steps(), values)), kind = ChartKind.Bars, target = prefs.stepsGoal.toDouble(), valueLabel = { formatNumber(it) })
        }
      }
      return@TabbedPluginList
    }
    item { SleepForm(ctx) }
    item { StepsForm(ctx) }
    val history = (entries.map { it.id } + imported.filter { it.value.steps != null }.map { it.id }).distinct().sortedDescending().take(60)
    if (history.isNotEmpty())
      item {
        SectionCard(HealthText.history(), Icons.Outlined.History) {
          val sleepById = entries.associate { it.id to it.value }
          val stepsById = imported.associate { it.id to it.value }
          history.forEach { id ->
            val sleep = sleepById[id]
            val day = stepsById[id]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Column(Modifier.weight(1f)) {
                Text(id.toDateOrNull()?.dayMonth() ?: id, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                  sleep?.let { "${it.bedtime}–${it.wake}" },
                  sleep?.quality?.let { HealthText.quality() + " $it/5" },
                  day?.steps?.let { HealthText.stepsValue(formatNumber(it.toDouble()))() + if (day.manualSteps) "" else " · Health Connect" }
                )
                if (details.isNotEmpty())
                  Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Text(sleep?.minutes()?.let(::formatDuration) ?: "", style = MaterialTheme.typography.titleSmall)
              if (sleep != null)
                IconButton(onClick = { log.delete(id) }) { Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
          }
        }
      }
  }
}

@Composable
private fun StepsForm(ctx: PluginContext) {
  var date by rememberSaveable { mutableStateOf(today().toString()) }
  var value by rememberSaveable { mutableStateOf("") }
  val steps = value.trim().toLongOrNull()?.takeIf { it in 0..200_000 }
  fun submit() {
    if (steps == null) return
    saveManualSteps(ctx, date, steps)
    value = ""
  }
  SectionCard(HealthText.logSteps(), Icons.Outlined.DirectionsWalk) {
    Text(HealthText.stepsHint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FieldsRow(minItemWidth = 140.dp) {
      BerillTextField(
        value, { value = it.filter(Char::isDigit).take(6) },
        label = { Text(HealthText.steps(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = value.isNotBlank() && steps == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        onSubmit = ::submit
      )
      DateField(date, { picked -> picked?.toDateOrNull()?.let { date = it.toString() } }, HealthText.date())
    }
    FilledTonalButton(enabled = steps != null, onClick = ::submit) { Text(HealthText.save()) }
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
