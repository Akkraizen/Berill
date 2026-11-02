package dev.akkraizen.berill.plugins.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.HealthSummary
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.health.HealthPlugin
import dev.akkraizen.berill.plugins.health.data.Dose
import dev.akkraizen.berill.plugins.health.data.Intake
import dev.akkraizen.berill.plugins.health.data.IntakeStatus
import dev.akkraizen.berill.plugins.health.data.WellbeingDay
import dev.akkraizen.berill.plugins.health.data.dosesOn
import dev.akkraizen.berill.plugins.health.data.intakes
import dev.akkraizen.berill.plugins.health.data.medications
import dev.akkraizen.berill.plugins.health.data.wellbeing
import dev.akkraizen.berill.plugins.health.i18n.HealthText

@Composable
internal fun TodayPage(ctx: PluginContext) {
  PluginList {
    item { DosesCard(ctx) }
    item { QuickWellbeingCard(ctx) }
    item { HealthSummary(ctx) }
  }
}

@Composable
internal fun rememberTodayDoses(ctx: PluginContext): List<Dose> {
  val meds by remember(ctx) { medications(ctx).observe() }.collectAsState(emptyList())
  val taken by remember(ctx) { intakes(ctx).observe() }.collectAsState(emptyList())
  val day = today()
  return remember(meds, taken, day) {
    dosesOn(day, meds.map { it.id to it.value }, taken.associate { it.id to it.value })
  }
}

internal fun toggleDose(ctx: PluginContext, dose: Dose, status: IntakeStatus = IntakeStatus.TAKEN) {
  val log = intakes(ctx)
  if (dose.intake?.status == status) log.delete(dose.id)
  else log.save(dose.id, Intake(dose.medicationId, dose.date, dose.time, status, nowMillis()))
}

@Composable
internal fun DosesCard(ctx: PluginContext) {
  val doses = rememberTodayDoses(ctx)
  SectionCard(HealthText.todayDoses(), Icons.Outlined.Medication) {
    if (doses.isEmpty()) EmptyHint(HealthText.noDosesToday())
    doses.forEach { dose -> DoseRow(ctx, dose) }
    TextButton(onClick = { ctx.nav.openPlugin(HealthPlugin.id, page = HealthPlugin.PAGE_MEDICATIONS) }) {
      Text(HealthText.manageMedications())
    }
  }
}

@Composable
internal fun DoseRow(ctx: PluginContext, dose: Dose) {
  val status = dose.intake?.status
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Checkbox(checked = status == IntakeStatus.TAKEN, onCheckedChange = { toggleDose(ctx, dose) })
    Column(Modifier.weight(1f)) {
      Text(
        dose.medication.name,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (status == IntakeStatus.SKIPPED) TextDecoration.LineThrough else null
      )
      val details = listOfNotNull(dose.time ?: HealthText.anyTime(), dose.medication.dose?.takeIf { it.isNotBlank() }, if (status == IntakeStatus.SKIPPED) HealthText.skipped() else null)
      Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    TextButton(onClick = { toggleDose(ctx, dose, IntakeStatus.SKIPPED) }) {
      Text(if (status == IntakeStatus.SKIPPED) HealthText.undo() else HealthText.skip())
    }
  }
}

@Composable
private fun QuickWellbeingCard(ctx: PluginContext) {
  val collection = remember(ctx) { wellbeing(ctx) }
  val key = today().toString()
  val day by remember(collection, key) { collection.observe(key) }.collectAsState(collection.get(key))
  val current = day ?: WellbeingDay(key)
  fun update(next: WellbeingDay) {
    if (next.isEmpty) collection.delete(key) else collection.save(key, next)
  }
  SectionCard(HealthText.howAreYou(), Icons.Outlined.SentimentSatisfied, onTitleClick = {
    ctx.nav.openPlugin(HealthPlugin.id, page = HealthPlugin.PAGE_WELLBEING)
  }) {
    RatingRow(HealthText.wellbeing(), current.wellbeing) { update(current.copy(wellbeing = it)) }
    RatingRow(HealthText.mood(), current.mood) { update(current.copy(mood = it)) }
    RatingRow(HealthText.energy(), current.energy) { update(current.copy(energy = it)) }
    if (current.symptoms.isNotEmpty())
      Text(current.symptoms.joinToString { "${it.name} (${severityLabel(it.severity)})" }, style = MaterialTheme.typography.bodySmall)
  }
}
