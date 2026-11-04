package dev.akkraizen.berill.plugins.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.recentDates
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.plugins.health.data.Insight
import dev.akkraizen.berill.plugins.health.data.InsightDay
import dev.akkraizen.berill.plugins.health.data.InsightFactor
import dev.akkraizen.berill.plugins.health.data.InsightMetric
import dev.akkraizen.berill.plugins.health.data.Strength
import dev.akkraizen.berill.plugins.health.data.activity
import dev.akkraizen.berill.plugins.health.data.insights
import dev.akkraizen.berill.plugins.health.data.rememberHealthPrefs
import dev.akkraizen.berill.plugins.health.data.sleepLog
import dev.akkraizen.berill.plugins.health.data.strength
import dev.akkraizen.berill.plugins.health.data.wellbeing
import dev.akkraizen.berill.plugins.health.i18n.HealthText
import kotlin.math.abs

@Composable
internal fun InsightsCard(ctx: PluginContext) {
  val prefs = rememberHealthPrefs(ctx)
  val moods by remember(ctx) { wellbeing(ctx).observe() }.collectAsState(emptyList())
  val imported by remember(ctx) { activity(ctx).observe() }.collectAsState(emptyList())
  val slept by remember(ctx) { sleepLog(ctx).observe() }.collectAsState(emptyList())
  val found = remember(moods, imported, slept, prefs.sleepGoalMinutes, prefs.stepsGoal) {
    val byMood = moods.associate { it.id to it.value }
    val byActivity = imported.associate { it.id to it.value }
    val bySleep = slept.associate { it.id to it.value }
    val days = recentDates(INSIGHT_DAYS).mapNotNull { date ->
      val key = date.toString()
      val mood = byMood[key] ?: return@mapNotNull null
      val activityDay = byActivity[key]
      InsightDay(activityDay?.sleepMinutes ?: bySleep[key]?.minutes()?.toLong(), activityDay?.steps, mood.wellbeing, mood.mood, mood.energy)
    }
    insights(days, prefs.sleepGoalMinutes, prefs.stepsGoal)
  }
  SectionCard(HealthText.insights(), Icons.Outlined.Insights) {
    Text(HealthText.insightsHint(INSIGHT_DAYS)(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (found.isEmpty()) EmptyHint(HealthText.insightsEmpty())
    found.filter { abs(it.difference) >= 0.2 }.ifEmpty { found.take(2) }.take(MAX_SHOWN).forEach { InsightRow(it) }
  }
}

@Composable
private fun InsightRow(insight: Insight) {
  val colors = MaterialTheme.colorScheme
  val better = insight.difference >= 0
  Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Ic(if (insight.factor == InsightFactor.SLEEP) Icons.Outlined.Bedtime else Icons.Outlined.DirectionsWalk, tint = colors.primary)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(insightSentence(insight), style = MaterialTheme.typography.bodyMedium)
        Text(
          listOfNotNull(
            HealthText.insightDays(insight.aboveDays, insight.belowDays)(),
            strengthLabel(strength(insight.correlation))
          ).joinToString(" · "),
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant
        )
      }
      Text(
        (if (better) "+" else "−") + formatNumber(abs(insight.difference)),
        style = MaterialTheme.typography.titleMedium,
        color = if (better) colors.primary else colors.error
      )
    }
  }
}

private fun metricLabel(metric: InsightMetric): String = when (metric) {
  InsightMetric.WELLBEING -> HealthText.wellbeing()
  InsightMetric.MOOD -> HealthText.mood()
  InsightMetric.ENERGY -> HealthText.energy()
}

internal fun insightSentence(insight: Insight): String {
  val threshold = if (insight.factor == InsightFactor.SLEEP) formatDuration(insight.threshold.toInt()) else formatNumber(insight.threshold)
  val metric = metricLabel(insight.metric).lowercase()
  val above = formatNumber(insight.above)
  val below = formatNumber(insight.below)
  return if (insight.factor == InsightFactor.SLEEP) HealthText.insightSleep(threshold, metric, above, below)()
  else HealthText.insightSteps(threshold, metric, above, below)()
}

private fun strengthLabel(strength: Strength): String? = when (strength) {
  Strength.NONE -> null
  Strength.WEAK -> HealthText.strengthWeak()
  Strength.MODERATE -> HealthText.strengthModerate()
  Strength.STRONG -> HealthText.strengthStrong()
}

private const val INSIGHT_DAYS = 90
private const val MAX_SHOWN = 4
