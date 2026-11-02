package dev.akkraizen.berill.plugins.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.health.data.DoseStyle
import dev.akkraizen.berill.plugins.health.data.REMINDER_LEADS
import dev.akkraizen.berill.plugins.health.data.SLEEP_GOALS
import dev.akkraizen.berill.plugins.health.data.STEP_GOALS
import dev.akkraizen.berill.plugins.health.data.rememberHealthPrefs
import dev.akkraizen.berill.plugins.health.data.setHealthPrefs
import dev.akkraizen.berill.plugins.health.i18n.HealthText

@Composable
internal fun HealthSettings(ctx: PluginContext) {
  val prefs = rememberHealthPrefs(ctx)
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Setting(HealthText.settingDoseStyle(), HealthText.settingDoseStyleHint()) {
      ChoiceChips(DoseStyle.entries, prefs.doseStyle, ::styleLabel, { setHealthPrefs(ctx, prefs.copy(doseStyle = it)) })
    }
    Toggle(HealthText.settingReminders(), HealthText.settingRemindersHint(), prefs.reminders) {
      setHealthPrefs(ctx, prefs.copy(reminders = it))
    }
    if (prefs.reminders)
      Setting(HealthText.settingLead(), null) {
        ChoiceChips(REMINDER_LEADS, prefs.reminderLeadMinutes, { if (it == 0) HealthText.leadOnTime() else HealthText.leadBefore(it)() }, {
          setHealthPrefs(ctx, prefs.copy(reminderLeadMinutes = it))
        })
      }
    Setting(HealthText.settingSleepGoal(), null) {
      ChoiceChips(SLEEP_GOALS, prefs.sleepGoalMinutes, ::formatDuration, { setHealthPrefs(ctx, prefs.copy(sleepGoalMinutes = it)) })
    }
    Setting(HealthText.settingStepsGoal(), null) {
      ChoiceChips(STEP_GOALS, prefs.stepsGoal, { formatNumber(it.toDouble()) }, { setHealthPrefs(ctx, prefs.copy(stepsGoal = it)) })
    }
    Toggle(HealthText.settingHomeSummary(), HealthText.settingHomeSummaryHint(), prefs.homeSummary) {
      setHealthPrefs(ctx, prefs.copy(homeSummary = it))
    }
  }
}

internal fun styleLabel(style: DoseStyle): String =
  when (style) {
    DoseStyle.CARDS -> HealthText.styleCards()
    DoseStyle.TIMELINE -> HealthText.styleTimeline()
    DoseStyle.CHIPS -> HealthText.styleChips()
  }

@Composable
private fun Setting(title: String, hint: String?, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    content()
  }
}

@Composable
private fun Toggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
