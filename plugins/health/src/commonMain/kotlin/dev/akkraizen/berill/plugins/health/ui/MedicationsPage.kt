package dev.akkraizen.berill.plugins.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.fields.parseClock
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.health.data.Medication
import dev.akkraizen.berill.plugins.health.data.MedicationKind
import dev.akkraizen.berill.plugins.health.data.intakes
import dev.akkraizen.berill.plugins.health.data.medications
import dev.akkraizen.berill.plugins.health.i18n.HealthText

private const val NEW = ""

@Composable
internal fun MedicationsPage(ctx: PluginContext) {
  val collection = remember(ctx) { medications(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  var editing by remember { mutableStateOf<String?>(null) }
  val sorted = all.sortedWith(compareBy({ !it.value.active }, { it.value.name.lowercase() }))

  PluginList {
    item {
      SectionCard(HealthText.pageMedications(), Icons.Outlined.Medication, trailing = {
        FilledTonalButton(onClick = { editing = NEW }) { Text(HealthText.add()) }
      }) {
        if (sorted.isEmpty()) EmptyHint(HealthText.noMedications())
        sorted.forEach { item -> MedicationRow(item) { editing = item.id } }
      }
    }
    item { IntakeHistory(ctx, all) }
  }

  editing?.let { id ->
    val current = if (id == NEW) null else all.firstOrNull { it.id == id }?.value
    MedicationDialog(
      initial = current,
      onSave = { value ->
        val key = if (id == NEW) ctx.data.newId() else id
        collection.save(key, value.copy(createdAt = current?.createdAt ?: nowMillis()))
        editing = null
      },
      onDelete = if (id == NEW) null else ({
        collection.delete(id)
        editing = null
      }),
      onDismiss = { editing = null }
    )
  }
}

@Composable
private fun MedicationRow(item: Item<Medication>, onClick: () -> Unit) {
  val med = item.value
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(Modifier.weight(1f)) {
      Text(med.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = if (med.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
      val schedule = med.sortedTimes().joinToString(", ").ifEmpty { HealthText.anyTime() }
      val details = listOfNotNull(
        kindLabel(med.kind),
        med.dose?.takeIf { it.isNotBlank() },
        schedule,
        if (med.everyDays > 1) HealthText.everyDays(med.everyDays)() else null,
        if (!med.active) HealthText.paused() else null
      )
      Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    IconButton(onClick = onClick) { Ic(BerillIcons.Edit, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
  }
}

@Composable
private fun IntakeHistory(ctx: PluginContext, meds: List<Item<Medication>>) {
  val log by remember(ctx) { intakes(ctx).observe() }.collectAsState(emptyList())
  if (log.isEmpty()) return
  val names = meds.associate { it.id to it.value.name }
  SectionCard(HealthText.history(), Icons.Outlined.History) {
    log.map { it.value }.sortedWith(compareByDescending<dev.akkraizen.berill.plugins.health.data.Intake> { it.date }.thenByDescending { it.time ?: "" })
      .take(40)
      .forEach { intake ->
        val day = intake.date.toDateOrNull()?.let { if (it == today()) HealthText.today() else it.toString() } ?: intake.date
        val status = if (intake.status == dev.akkraizen.berill.plugins.health.data.IntakeStatus.TAKEN) HealthText.taken() else HealthText.skipped()
        Text(
          listOfNotNull(day, intake.time, names[intake.medication] ?: HealthText.deleted(), status).joinToString(" · "),
          style = MaterialTheme.typography.bodySmall
        )
      }
  }
}

@Composable
private fun MedicationDialog(initial: Medication?, onSave: (Medication) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var kind by remember { mutableStateOf(initial?.kind ?: MedicationKind.MEDICATION) }
  var dose by remember { mutableStateOf(initial?.dose ?: "") }
  var times by remember { mutableStateOf(initial?.sortedTimes()?.joinToString(", ") ?: "") }
  var every by remember { mutableStateOf((initial?.everyDays ?: 1).toString()) }
  var start by remember { mutableStateOf(initial?.start) }
  var end by remember { mutableStateOf(initial?.end) }
  var active by remember { mutableStateOf(initial?.active ?: true) }
  var notes by remember { mutableStateOf(initial?.notes ?: "") }
  var confirmDelete by remember { mutableStateOf(false) }

  val parsedTimes = times.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
  val timesValid = parsedTimes.all { parseClock(it) != null }
  val everyDays = every.trim().toIntOrNull()?.takeIf { it in 1..365 }
  val startDate = start.toDateOrNull()
  val endDate = end.toDateOrNull()
  val rangeValid = startDate == null || endDate == null || startDate <= endDate
  val valid = name.isNotBlank() && timesValid && everyDays != null && rangeValid

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) HealthText.newMedication() else HealthText.editMedication()) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(name, { name = it.take(120) }, label = { Text(HealthText.name()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ChoiceChips(MedicationKind.entries, kind, ::kindLabel, { kind = it })
        BerillTextField(dose, { dose = it.take(60) }, label = { Text(HealthText.dose()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        BerillTextField(
          times, { times = it.take(120) },
          label = { Text(HealthText.times()) },
          placeholder = { Text("08:00, 20:00") },
          supportingText = { Text(HealthText.timesHint()) },
          isError = !timesValid,
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        BerillTextField(
          every, { every = it.filter(Char::isDigit).take(3) },
          label = { Text(HealthText.everyDaysLabel()) },
          isError = everyDays == null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        DateField(start, { start = it }, HealthText.startDate())
        DateField(end, { end = it }, HealthText.endDate())
        if (!rangeValid) Text(HealthText.badRange(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Switch(checked = active, onCheckedChange = { active = it })
          Text(HealthText.activeLabel())
        }
        BerillTextField(notes, { notes = it.take(500) }, label = { Text(HealthText.notes()) }, modifier = Modifier.fillMaxWidth())
        if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text(UiText.delete(), color = MaterialTheme.colorScheme.error) }
      }
    },
    confirmButton = {
      TextButton(enabled = valid, onClick = {
        onSave(
          Medication(
            name = name.trim(),
            kind = kind,
            dose = dose.trim().ifEmpty { null },
            times = parsedTimes,
            everyDays = everyDays ?: 1,
            start = startDate?.toString() ?: if ((everyDays ?: 1) > 1) today().toString() else null,
            end = endDate?.toString(),
            active = active,
            notes = notes.trim().ifEmpty { null }
          ).let { it.copy(times = it.sortedTimes()) }
        )
      }) { Text(HealthText.save()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.cancel()) } }
  )

  if (confirmDelete && onDelete != null)
    ConfirmDialog(HealthText.deleteMedication(), HealthText.deleteMedicationText(name)(), onConfirm = onDelete, onDismiss = { confirmDelete = false })
}
