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
import dev.akkraizen.berill.plugins.health.data.summary
import dev.akkraizen.berill.plugins.health.i18n.HealthText

@Composable
internal fun HealthHomeCard(ctx: PluginContext) {
  val doses = rememberTodayDoses(ctx)
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (doses.isEmpty()) EmptyHint(HealthText.noDosesToday())
    else {
      val done = doses.count { it.intake?.status == IntakeStatus.TAKEN }
      Text(HealthText.dosesDone(done, doses.size)(), style = MaterialTheme.typography.labelLarge)
      doses.filter { it.intake == null }.take(4).forEach { DoseRow(ctx, it) }
    }
    summary(ctx, today())?.let { day ->
      val parts = listOfNotNull(
        day.steps?.let { HealthText.stepsValue(it)() },
        day.sleepMinutes?.let { HealthText.sleptValue(formatDuration(it.toInt()))() }
      )
      if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
