package dev.akkraizen.berill.plugins.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.RawRecord
import dev.akkraizen.berill.api.ui.formatMinutes
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.health.data.MedicationKind
import dev.akkraizen.berill.plugins.health.data.RATINGS
import dev.akkraizen.berill.plugins.health.data.SleepEntry
import dev.akkraizen.berill.plugins.health.data.VitalEntry
import dev.akkraizen.berill.plugins.health.data.VitalType
import dev.akkraizen.berill.plugins.health.data.WellbeingDay
import dev.akkraizen.berill.plugins.health.i18n.HealthText
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

private fun <T> RawRecord.decode(serializer: KSerializer<T>): T? = runCatching { json.decodeFromJsonElement(serializer, fields) }.getOrNull()

fun formatNumber(value: Double): String {
  val rounded = (value * 10).roundToLong() / 10.0
  return if (abs(rounded % 1.0) < 1e-9) rounded.toLong().toString() else rounded.toString()
}

fun formatDuration(minutes: Int): String = formatMinutes(minutes)

fun vitalLabel(type: VitalType): String =
  when (type) {
    VitalType.PULSE -> HealthText.vitalPulse()
    VitalType.BLOOD_PRESSURE -> HealthText.vitalPressure()
    VitalType.TEMPERATURE -> HealthText.vitalTemperature()
    VitalType.OXYGEN -> HealthText.vitalOxygen()
    VitalType.GLUCOSE -> HealthText.vitalGlucose()
  }

fun vitalUnit(type: VitalType): String =
  when (type) {
    VitalType.PULSE -> HealthText.unitPulse()
    VitalType.BLOOD_PRESSURE -> HealthText.unitPressure()
    VitalType.TEMPERATURE -> HealthText.unitTemperature()
    VitalType.OXYGEN -> HealthText.unitOxygen()
    VitalType.GLUCOSE -> HealthText.unitGlucose()
  }

fun vitalValue(entry: VitalEntry): String {
  val main = formatNumber(entry.value)
  val value = entry.value2?.let { "$main/${formatNumber(it)}" } ?: main
  return "$value ${vitalUnit(entry.type)}"
}

fun kindLabel(kind: MedicationKind): String =
  when (kind) {
    MedicationKind.MEDICATION -> HealthText.kindMedication()
    MedicationKind.SUPPLEMENT -> HealthText.kindSupplement()
  }

fun severityLabel(severity: Int): String =
  when (severity) {
    1 -> HealthText.severityMild()
    2 -> HealthText.severityModerate()
    else -> HealthText.severitySevere()
  }

fun describeVital(record: RawRecord): String =
  record.decode(VitalEntry.serializer())?.let { "${vitalLabel(it.type)}: ${vitalValue(it)}" } ?: ""

fun describeWellbeing(record: RawRecord): String =
  record.decode(WellbeingDay.serializer())?.let { day ->
    listOfNotNull(
      day.wellbeing?.let { HealthText.wellbeing() + " $it/5" },
      day.mood?.let { HealthText.mood() + " $it/5" },
      day.energy?.let { HealthText.energy() + " $it/5" },
      day.symptoms.takeIf { it.isNotEmpty() }?.joinToString { it.name }
    ).joinToString(" · ")
  } ?: ""

fun describeSleep(record: RawRecord): String =
  record.decode(SleepEntry.serializer())?.let { entry ->
    listOfNotNull("${entry.bedtime}–${entry.wake}", entry.minutes()?.let(::formatDuration), entry.quality?.let { HealthText.quality() + " $it/5" })
      .joinToString(" · ")
  } ?: ""

@Composable
internal fun RatingRow(title: String, value: Int?, onChange: (Int?) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    ChoiceChips(RATINGS.toList(), value ?: 0, { "$it" }, { picked -> onChange(if (picked == value) null else picked) })
  }
}
