package dev.akkraizen.berill.plugins.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.health.data.IntakeStatus
import dev.akkraizen.berill.plugins.health.data.rememberHealthPrefs
import dev.akkraizen.berill.plugins.health.data.summary
import dev.akkraizen.berill.plugins.health.i18n.HealthText

@Composable
internal fun HealthHomeCard(ctx: PluginContext) {
  val doses = rememberTodayDoses(ctx)
  val prefs = rememberHealthPrefs(ctx)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (doses.isEmpty()) EmptyHint(HealthText.noDosesToday())
    else {
      DayProgress(doses)
      val open = doses.filter { it.intake?.status != IntakeStatus.TAKEN }.take(4)
      if (open.isNotEmpty()) DoseList(ctx, open, prefs.doseStyle)
    }
    if (prefs.homeSummary) summary(ctx, today())?.let { day ->
      val parts = listOfNotNull(
        day.steps?.let { HealthText.stepsOfGoal(it, prefs.stepsGoal)() },
        day.sleepMinutes?.let { HealthText.sleptValue(formatDuration(it.toInt()))() }
      )
      if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
