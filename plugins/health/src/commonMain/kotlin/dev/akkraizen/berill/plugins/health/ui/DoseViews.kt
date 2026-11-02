package dev.akkraizen.berill.plugins.health.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.fields.clockText
import dev.akkraizen.berill.api.ui.fields.parseClock
import dev.akkraizen.berill.api.ui.localDateTime
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.health.data.Dose
import dev.akkraizen.berill.plugins.health.data.DoseStyle
import dev.akkraizen.berill.plugins.health.data.IntakeStatus
import dev.akkraizen.berill.plugins.health.data.MedicationKind
import dev.akkraizen.berill.plugins.health.i18n.HealthText

internal enum class DoseState { PENDING, DUE, TAKEN, SKIPPED }

internal fun Dose.state(nowMinutes: Int = localDateTime(nowMillis()).time.let { it.hour * 60 + it.minute }): DoseState =
  when (intake?.status) {
    IntakeStatus.TAKEN -> DoseState.TAKEN
    IntakeStatus.SKIPPED -> DoseState.SKIPPED
    null -> {
      val clock = parseClock(time)
      if (date == today().toString() && clock != null && clock.hour * 60 + clock.minute <= nowMinutes) DoseState.DUE else DoseState.PENDING
    }
  }

private fun Dose.takenAt(): String? = intake?.takeIf { it.status == IntakeStatus.TAKEN && it.at > 0 }?.let { localDateTime(it.at).time.clockText() }

private fun Dose.icon() = if (medication.kind == MedicationKind.SUPPLEMENT) Icons.Outlined.Spa else Icons.Outlined.Medication

@Composable
internal fun DoseList(ctx: PluginContext, doses: List<Dose>, style: DoseStyle) {
  when (style) {
    DoseStyle.CARDS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { doses.forEach { DoseCard(ctx, it) } }
    DoseStyle.TIMELINE -> DoseTimeline(ctx, doses)
    DoseStyle.CHIPS -> DoseChips(ctx, doses)
  }
}

@Composable
internal fun DayProgress(doses: List<Dose>) {
  if (doses.isEmpty()) return
  val done = doses.count { it.intake?.status == IntakeStatus.TAKEN }
  val due = doses.count { it.state() == DoseState.DUE }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(HealthText.dosesDone(done, doses.size)(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
      if (due > 0) Text(HealthText.dueCount(due)(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
      else if (done == doses.size) Text(HealthText.allTaken(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
    ProgressLine(done.toFloat() / doses.size)
  }
}

@Composable
private fun DoseCard(ctx: PluginContext, dose: Dose) {
  val state = dose.state()
  val colors = MaterialTheme.colorScheme
  val container by animateColorAsState(
    when (state) {
      DoseState.TAKEN -> colors.primaryContainer
      DoseState.DUE -> colors.errorContainer.copy(alpha = 0.55f)
      DoseState.SKIPPED -> colors.surfaceContainerHighest.copy(alpha = 0.6f)
      DoseState.PENDING -> colors.surfaceContainerHighest
    },
    label = "dose-card"
  )
  Surface(
    onClick = { toggleDose(ctx, dose) },
    shape = RoundedCornerShape(20.dp),
    color = container,
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      DoseMark(dose, state, size = 44)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          dose.medication.name,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textDecoration = if (state == DoseState.SKIPPED) TextDecoration.LineThrough else null
        )
        Text(details(dose, state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      dose.time?.let { TimeBadge(it, state) }
      if (state != DoseState.TAKEN) SkipButton(ctx, dose, state)
    }
  }
}

@Composable
private fun DoseMark(dose: Dose, state: DoseState, size: Int, onClick: (() -> Unit)? = null) {
  val colors = MaterialTheme.colorScheme
  val fill by animateColorAsState(
    when (state) {
      DoseState.TAKEN -> colors.primary
      DoseState.SKIPPED -> colors.outlineVariant
      DoseState.DUE -> colors.error.copy(alpha = 0.12f)
      DoseState.PENDING -> Color.Transparent
    },
    label = "dose-mark"
  )
  val tint = when (state) {
    DoseState.TAKEN -> colors.onPrimary
    DoseState.SKIPPED -> colors.onSurfaceVariant
    DoseState.DUE -> colors.error
    DoseState.PENDING -> colors.primary
  }
  val pop by animateFloatAsState(if (state == DoseState.TAKEN) 1f else 0.92f, label = "dose-pop")
  Box(
    Modifier
      .size(size.dp)
      .scale(pop)
      .clip(CircleShape)
      .background(fill)
      .border(2.dp, if (state == DoseState.PENDING) colors.primary.copy(alpha = 0.5f) else Color.Transparent, CircleShape)
      .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    contentAlignment = Alignment.Center
  ) {
    Ic(
      when (state) {
        DoseState.TAKEN -> Icons.Outlined.Check
        DoseState.SKIPPED -> Icons.Outlined.Close
        else -> dose.icon()
      },
      size = (size * 0.5f).dp,
      tint = tint
    )
  }
}

@Composable
private fun SkipButton(ctx: PluginContext, dose: Dose, state: DoseState) {
  val skipped = state == DoseState.SKIPPED
  IconButton(onClick = { toggleDose(ctx, dose, IntakeStatus.SKIPPED) }) {
    Icon(
      if (skipped) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.SkipNext,
      contentDescription = if (skipped) HealthText.undo() else HealthText.skip(),
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun TimeBadge(text: String, state: DoseState) {
  val colors = MaterialTheme.colorScheme
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (state == DoseState.DUE) colors.error else colors.surface.copy(alpha = 0.7f),
    contentColor = if (state == DoseState.DUE) colors.onError else colors.onSurface
  ) {
    Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 1)
  }
}

private fun details(dose: Dose, state: DoseState): String =
  listOfNotNull(
    dose.medication.dose?.takeIf { it.isNotBlank() },
    when (state) {
      DoseState.TAKEN -> dose.takenAt()?.let { HealthText.takenAt(it)() } ?: HealthText.taken()
      DoseState.SKIPPED -> HealthText.skipped()
      DoseState.DUE -> HealthText.due()
      DoseState.PENDING -> null
    }
  ).joinToString(" · ").ifEmpty { kindLabel(dose.medication.kind) }

private enum class DayPart { MORNING, AFTERNOON, EVENING, ANY }

private fun Dose.part(): DayPart {
  val clock = parseClock(time) ?: return DayPart.ANY
  return when {
    clock.hour < 12 -> DayPart.MORNING
    clock.hour < 18 -> DayPart.AFTERNOON
    else -> DayPart.EVENING
  }
}

private fun DayPart.label(): String =
  when (this) {
    DayPart.MORNING -> HealthText.morning()
    DayPart.AFTERNOON -> HealthText.afternoon()
    DayPart.EVENING -> HealthText.evening()
    DayPart.ANY -> HealthText.anyTime()
  }

@Composable
private fun DoseTimeline(ctx: PluginContext, doses: List<Dose>) {
  val grouped = doses.groupBy { it.part() }
  val groups = DayPart.entries.mapNotNull { part -> grouped[part]?.let { part to it } }
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    groups.forEach { (part, items) ->
      Text(part.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      Column {
        items.forEachIndexed { index, dose ->
          val state = dose.state()
          Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { toggleDose(ctx, dose) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(dose.time ?: "", style = MaterialTheme.typography.labelMedium, color = if (state == DoseState.DUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Box(Modifier.width(2.dp).height(8.dp).background(if (index == 0) Color.Transparent else MaterialTheme.colorScheme.outlineVariant))
              DoseMark(dose, state, size = 28)
              Box(Modifier.width(2.dp).height(8.dp).background(if (index == items.lastIndex) Color.Transparent else MaterialTheme.colorScheme.outlineVariant))
            }
            Column(Modifier.weight(1f)) {
              Text(
                dose.medication.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (state == DoseState.SKIPPED) TextDecoration.LineThrough else null,
                color = if (state == DoseState.TAKEN) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
              )
              Text(details(dose, state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state != DoseState.TAKEN) SkipButton(ctx, dose, state)
          }
        }
      }
    }
  }
}

@Composable
private fun DoseChips(ctx: PluginContext, doses: List<Dose>) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    doses.forEach { dose ->
      val state = dose.state()
      FilterChip(
        selected = state == DoseState.TAKEN,
        onClick = { toggleDose(ctx, dose) },
        label = {
          Text(
            listOfNotNull(dose.time, dose.medication.name).joinToString(" · "),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (state == DoseState.SKIPPED) TextDecoration.LineThrough else null
          )
        },
        leadingIcon = { Ic(if (state == DoseState.TAKEN) Icons.Outlined.Check else dose.icon(), size = 18.dp) },
        colors = if (state == DoseState.DUE) FilterChipDefaults.filterChipColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          labelColor = MaterialTheme.colorScheme.onErrorContainer
        ) else FilterChipDefaults.filterChipColors()
      )
    }
  }
}
