package dev.akkraizen.berill.plugins.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.layout.QuickActionDialog
import dev.akkraizen.berill.api.ui.layout.QuickDateField
import dev.akkraizen.berill.api.ui.layout.QuickForm
import dev.akkraizen.berill.api.ui.layout.QuickSaveButtons
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.state.LocalQuickEntries
import dev.akkraizen.berill.api.ui.state.saveQuickEntry
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.health.data.SEVERITIES
import dev.akkraizen.berill.plugins.health.data.Symptom
import dev.akkraizen.berill.plugins.health.data.VitalType
import dev.akkraizen.berill.plugins.health.data.WellbeingDay
import dev.akkraizen.berill.plugins.health.data.vitals
import dev.akkraizen.berill.plugins.health.data.wellbeing
import dev.akkraizen.berill.plugins.health.i18n.HealthText
import kotlinx.coroutines.CancellationException

@Composable
internal fun QuickVitalDialog(ctx: PluginContext, onDismiss: () -> Unit, onError: (Exception) -> Unit) {
  val entries = LocalQuickEntries.current
  val collection = remember(ctx) { vitals(ctx) }
  val draft = remember {
    VitalDraft(collection.all().maxByOrNull { it.value.createdAt }?.value?.type ?: VitalType.PULSE)
  }
  val problem = draft.entry().problem()
  fun save(again: Boolean) {
    val entry = draft.entry()
    if (entry.problem() != null) return
    try {
      saveQuickEntry(entries, collection, ctx.data.newId(), entry.copy(createdAt = nowMillis()))
      draft.clear()
      if (!again) onDismiss()
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) { onError(e) }
  }
  QuickActionDialog(HealthText.quickVitals(), Icons.Outlined.MonitorHeart, onDismiss) {
    QuickForm {
      ChoiceChips(VitalType.entries, draft.type, ::vitalLabel, { picked ->
        if (picked != draft.type) {
          draft.type = picked
          draft.clear()
        }
      })
      VitalInputs(draft) { save(again = false) }
      QuickSaveButtons(problem == null, onDismiss, ::save)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickWellbeingDialog(ctx: PluginContext, onDismiss: () -> Unit, onError: (Exception) -> Unit) {
  val collection = remember(ctx) { wellbeing(ctx) }
  var date by remember { mutableStateOf(today().toString()) }
  var day by remember { mutableStateOf(collection.get(date) ?: WellbeingDay(date)) }
  var symptom by remember { mutableStateOf("") }
  val stored = remember(date) { collection.get(date) }
  val valid = date.toDateOrNull() != null && (day != (stored ?: WellbeingDay(date)) || symptom.isNotBlank())

  fun addSymptom() {
    val name = symptom.trim()
    if (name.isEmpty()) return
    day = day.copy(symptoms = day.symptoms + Symptom(name, 1))
    symptom = ""
  }

  fun save() {
    try {
      addSymptom()
      val key = date.toDateOrNull()?.toString() ?: return
      val value = day.copy(date = key, note = day.note?.trim()?.ifEmpty { null })
      if (value.isEmpty) collection.delete(key) else collection.save(key, value)
      onDismiss()
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) { onError(e) }
  }

  QuickActionDialog(HealthText.howAreYou(), Icons.Outlined.SentimentSatisfied, onDismiss) {
    QuickForm {
      QuickDateField(date) { picked ->
        date = picked
        picked.toDateOrNull()?.let { day = collection.get(it.toString()) ?: WellbeingDay(it.toString()) }
        symptom = ""
      }
      RatingRow(HealthText.wellbeing(), day.wellbeing) { day = day.copy(wellbeing = it) }
      RatingRow(HealthText.mood(), day.mood) { day = day.copy(mood = it) }
      RatingRow(HealthText.energy(), day.energy) { day = day.copy(energy = it) }
      Text(HealthText.symptoms(), style = MaterialTheme.typography.labelLarge)
      day.symptoms.forEachIndexed { index, item ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ChoiceChips(SEVERITIES.toList(), item.severity, ::severityLabel, { severity ->
              day = day.copy(symptoms = day.symptoms.mapIndexed { i, s -> if (i == index) s.copy(severity = severity) else s })
            })
          }
          IconButton(onClick = { day = day.copy(symptoms = day.symptoms.filterIndexed { i, _ -> i != index }) }) {
            Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(symptom, { symptom = it.take(80) }, label = { Text(HealthText.symptomName()) }, singleLine = true, modifier = Modifier.weight(1f), onSubmit = ::addSymptom)
        TextButton(onClick = ::addSymptom, enabled = symptom.isNotBlank()) { Text(HealthText.add()) }
      }
      BerillTextField(day.note ?: "", { day = day.copy(note = it.take(1000)) }, label = { Text(HealthText.note()) }, modifier = Modifier.fillMaxWidth())
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text(UiText.cancel()) }
        FilledTonalButton(onClick = ::save, enabled = valid) { Text(HealthText.save()) }
      }
    }
  }
}
