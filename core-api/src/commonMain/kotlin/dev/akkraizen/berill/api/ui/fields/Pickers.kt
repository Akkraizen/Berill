package dev.akkraizen.berill.api.ui.fields

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.toDateOrNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

internal expect fun calendarLocale(code: String): CalendarLocale

private fun LocalDate.utcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun dateOfUtcMillis(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

fun parseClock(text: String?): LocalTime? {
  val parts = text?.trim()?.split(':') ?: return null
  if (parts.size != 2) return null
  val h = parts[0].toIntOrNull() ?: return null
  val m = parts[1].toIntOrNull() ?: return null
  if (h !in 0..23 || m !in 0..59) return null
  return LocalTime(h, m)
}

fun LocalTime.clockText(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerPopup(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
  val code = I18n.language.code
  val locale = calendarLocale(if (code == "en") "en-GB" else if ('-' in code) code else "$code-${code.uppercase()}")
  val state = remember(locale) { DatePickerState(locale = locale, initialSelectedDateMillis = initial?.utcMillis()) }
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        enabled = state.selectedDateMillis != null,
        onClick = {
          state.selectedDateMillis?.let { onPick(dateOfUtcMillis(it)) }
          onDismiss()
        }
      ) { Text(UiText.done()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.cancel()) } }
  ) {
    DatePicker(
      state,
      title = { Text(UiText.pickDate(), Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
      headline = {
        Text(
          state.selectedDateMillis?.let { dateOfUtcMillis(it) }?.let { "${it.dayMonth()} ${it.year}" }.orEmpty(),
          Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
          style = MaterialTheme.typography.headlineMedium,
          maxLines = 1
        )
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerPopup(initial: LocalTime?, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
  val state = rememberTimePickerState(initial?.hour ?: 9, initial?.minute ?: 0, is24Hour = true)
  TimePickerDialog(
    onDismissRequest = onDismiss,
    title = { Text(UiText.pickTime()) },
    confirmButton = {
      TextButton(
        onClick = {
          onPick(LocalTime(state.hour, state.minute))
          onDismiss()
        }
      ) { Text(UiText.done()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.cancel()) } }
  ) {
    TimePicker(state, layoutType = TimePickerLayoutType.Vertical)
  }
}

@Composable
fun DatePickerButton(value: String?, enabled: Boolean = true, onPick: (String) -> Unit) {
  var open by remember { mutableStateOf(false) }
  IconButton(onClick = { open = true }, enabled = enabled) {
    Icon(Icons.Outlined.CalendarMonth, contentDescription = UiText.pickDate())
  }
  if (open) DatePickerPopup(value.toDateOrNull(), { onPick(it.toString()) }, { open = false })
}

@Composable
fun TimePickerButton(value: String?, enabled: Boolean = true, onPick: (String) -> Unit) {
  var open by remember { mutableStateOf(false) }
  IconButton(onClick = { open = true }, enabled = enabled) {
    Icon(Icons.Outlined.Schedule, contentDescription = UiText.pickTime())
  }
  if (open) TimePickerPopup(parseClock(value), { onPick(it.clockText()) }, { open = false })
}
