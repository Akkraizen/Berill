package dev.akkraizen.berill.plugins.planner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.DAYS_SHORT
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.fields.DatePickerButton
import dev.akkraizen.berill.api.ui.fields.DateTextField
import dev.akkraizen.berill.api.ui.fields.TimeField
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.weekdayIndex
import dev.akkraizen.berill.plugins.planner.data.BIRTHDAY
import dev.akkraizen.berill.plugins.planner.data.CalendarEvent
import dev.akkraizen.berill.plugins.planner.data.DAILY
import dev.akkraizen.berill.plugins.planner.data.GoogleCalendarInfo
import dev.akkraizen.berill.plugins.planner.data.MONTHLY
import dev.akkraizen.berill.plugins.planner.data.Repeat
import dev.akkraizen.berill.plugins.planner.data.WEEKLY
import dev.akkraizen.berill.plugins.planner.data.YEARLY
import dev.akkraizen.berill.plugins.planner.data.hhmm
import dev.akkraizen.berill.plugins.planner.data.isLeap
import dev.akkraizen.berill.plugins.planner.data.parseTime
import dev.akkraizen.berill.plugins.planner.i18n.CalendarText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

private const val LOCAL = ""
private const val NO_REPEAT = ""
private const val LEAP_YEAR = 2000

private fun repeatLabel(freq: String): String =
  when (freq) {
    DAILY -> CalendarText.repeatDaily()
    WEEKLY -> CalendarText.repeatWeekly()
    MONTHLY -> CalendarText.repeatMonthly()
    YEARLY -> CalendarText.repeatYearly()
    else -> CalendarText.repeatNone()
  }

internal fun parseDayMonth(text: String, year: Int?): LocalDate? {
  val parts = text.trim().split('.', '/', '-').filter { it.isNotBlank() }
  if (parts.size != 2) return null
  val day = parts[0].toIntOrNull() ?: return null
  val month = parts[1].toIntOrNull() ?: return null
  if (month !in 1..12 || day !in 1..31) return null
  if (year != null && month == 2 && day == 29 && !isLeap(year)) return null
  return runCatching { LocalDate(year ?: LEAP_YEAR, month, day) }.getOrNull()
}

private fun dayMonthText(date: LocalDate?): String =
  date?.let { "${it.day.toString().padStart(2, '0')}.${(it.month.ordinal + 1).toString().padStart(2, '0')}" }.orEmpty()

@Composable
private fun Field(
  value: String,
  onChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier.fillMaxWidth(),
  placeholder: String? = null,
  isError: Boolean = false,
  error: String? = null,
  number: Boolean = false,
  trailing: (@Composable () -> Unit)? = null
) {
  BerillTextField(
    value,
    onChange,
    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    singleLine = true,
    isError = isError,
    supportingText = error?.let { { Text(it) } },
    trailingIcon = trailing,
    keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    modifier = modifier
  )
}

@Composable
internal fun EventDialog(
  initial: CalendarEvent,
  isNew: Boolean,
  occurrence: LocalDate?,
  calendars: List<GoogleCalendarInfo>,
  defaultCalendar: String?,
  onDismiss: () -> Unit,
  onSave: (CalendarEvent) -> Unit,
  onDelete: () -> Unit,
  onSkip: () -> Unit
) {
  var birthday by remember { mutableStateOf(initial.isBirthday) }
  var title by remember { mutableStateOf(initial.title) }
  var allDay by remember { mutableStateOf(initial.allDay) }
  var date by remember { mutableStateOf(initial.date) }
  var endDate by remember { mutableStateOf(initial.endDate ?: initial.date) }
  var start by remember { mutableStateOf(initial.start ?: "09:00") }
  var end by remember { mutableStateOf(initial.end ?: "10:00") }
  var location by remember { mutableStateOf(initial.location) }
  var notes by remember { mutableStateOf(initial.notes) }
  var dayMonth by remember { mutableStateOf(if (initial.isBirthday) dayMonthText(initial.firstDay) else "") }
  var birthYear by remember { mutableStateOf(initial.birthYear?.toString().orEmpty()) }
  var freq by remember { mutableStateOf(initial.repeat?.freq ?: NO_REPEAT) }
  var interval by remember { mutableStateOf((initial.repeat?.interval ?: 1).toString()) }
  var weekdays by remember {
    mutableStateOf(initial.repeat?.days?.toSet() ?: initial.firstDay?.let { setOf(it.weekdayIndex + 1) }.orEmpty())
  }
  var until by remember { mutableStateOf(initial.repeat?.until.orEmpty()) }
  val linked = initial.googleId != null
  val writable = calendars.filter { it.writable }
  var calendar by remember {
    mutableStateOf(initial.calendarId ?: if (isNew) defaultCalendar ?: LOCAL else LOCAL)
  }
  var askDelete by remember { mutableStateOf(false) }

  val startDay = date.toDateOrNull()
  val lastDay = endDate.toDateOrNull()
  val startT = parseTime(start)
  val endT = parseTime(end)
  val ordered = when {
    startDay == null || lastDay == null -> true
    allDay -> lastDay >= startDay
    startT == null || endT == null -> true
    else -> LocalDateTime(lastDay, endT) > LocalDateTime(startDay, startT)
  }
  val year = birthYear.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
  val yearValid = birthYear.isBlank() || (year != null && year in 1900..today().year)
  val birthDate = parseDayMonth(dayMonth, year)
  val repeating = freq != NO_REPEAT
  val intervalValue = interval.trim().toIntOrNull()
  val untilDay = until.trim().takeIf { it.isNotEmpty() }?.toDateOrNull()
  val repeatValid = !repeating || (
    intervalValue != null && intervalValue in 1..99 &&
      (until.isBlank() || (untilDay != null && startDay != null && untilDay >= startDay))
    )
  val valid = if (birthday)
    title.isNotBlank() && birthDate != null && yearValid
  else
    title.isNotBlank() && startDay != null && lastDay != null &&
      (allDay || (startT != null && endT != null)) && ordered && repeatValid

  val confirm = {
    if (valid) {
      val calendarId = if (linked) initial.calendarId else calendar.takeIf { it != LOCAL }
      val event = if (birthday) {
        initial.copy(
          title = title.trim(),
          date = birthDate.toString(),
          endDate = null,
          start = null,
          end = null,
          notes = notes.trim(),
          calendarId = calendarId,
          kind = BIRTHDAY,
          birthYear = year,
          repeat = Repeat(YEARLY),
          exceptions = emptyList()
        )
      } else {
        val d = startDay!!
        initial.copy(
          title = title.trim(),
          date = d.toString(),
          endDate = lastDay!!.takeIf { it != d }?.toString(),
          start = if (allDay) null else startT!!.hhmm(),
          end = if (allDay) null else endT!!.hhmm(),
          location = location.trim(),
          notes = notes.trim(),
          calendarId = calendarId,
          kind = null,
          birthYear = null,
          repeat = if (repeating)
            Repeat(freq, intervalValue ?: 1, if (freq == WEEKLY) weekdays.sorted() else emptyList(), untilDay?.toString())
          else
            null,
          exceptions = if (repeating) initial.exceptions else emptyList()
        )
      }
      onSave(event)
    }
  }

  DialogKeys(onConfirm = confirm, confirmEnabled = valid, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        when {
          isNew && birthday -> CalendarText.newBirthday()
          isNew -> CalendarText.newEvent()
          birthday -> CalendarText.birthday()
          else -> CalendarText.event()
        }
      )
    },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!linked)
          ChoiceChips(
            listOf(false, true),
            birthday,
            { if (it) CalendarText.birthday() else CalendarText.event() },
            { birthday = it },
            icon = { if (it) Icons.Outlined.Cake else Icons.Outlined.Event }
          )
        if (!isNew && initial.recurring && !birthday)
          Text(CalendarText.seriesNote(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Field(title, { title = it }, if (birthday) CalendarText.name() else CalendarText.eventTitle())
        if (birthday) {
          FieldsRow {
            Field(
              dayMonth,
              { dayMonth = it },
              CalendarText.birthdayDate(),
              placeholder = CalendarText.dayMonthPlaceholder(),
              isError = dayMonth.isNotBlank() && birthDate == null,
              trailing = {
                DatePickerButton(birthDate?.let { if (year == null) null else it.toString() }) { picked ->
                  val d = picked.toDateOrNull() ?: return@DatePickerButton
                  dayMonth = dayMonthText(d)
                  birthYear = d.year.toString()
                }
              }
            )
            Field(
              birthYear,
              { birthYear = it.filter(Char::isDigit).take(4) },
              CalendarText.birthYear(),
              placeholder = CalendarText.optional(),
              isError = !yearValid,
              number = true
            )
          }
        } else {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(CalendarText.allDay(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = allDay, onCheckedChange = { allDay = it })
          }
          FieldsRow(minItemWidth = 160.dp) {
            DateTextField(
              date,
              {
                val before = endDate == date
                date = it
                if (before) endDate = it
              },
              CalendarText.startDate(),
              isError = startDay == null
            )
            DateTextField(endDate, { endDate = it }, CalendarText.endDate(), isError = lastDay == null || (!ordered && allDay))
          }
          if (!allDay)
            FieldsRow(minItemWidth = 130.dp) {
              TimeField(start, { start = it }, CalendarText.startTime())
              TimeField(
                end,
                { end = it },
                CalendarText.endTime(),
                isError = endT == null || !ordered,
                supportingText = if (!ordered) CalendarText.endBeforeStart() else null
              )
            }
          DropdownField(
            CalendarText.repeat(),
            listOf(NO_REPEAT, DAILY, WEEKLY, MONTHLY, YEARLY),
            freq,
            ::repeatLabel,
            { freq = it },
            Modifier.fillMaxWidth()
          )
          if (repeating) {
            if (freq == WEEKLY)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DAYS_SHORT.forEachIndexed { i, name ->
                  val iso = i + 1
                  BerillChip(
                    selected = iso in weekdays,
                    onClick = {
                      val next = if (iso in weekdays) weekdays - iso else weekdays + iso
                      if (next.isNotEmpty()) weekdays = next
                    },
                    label = { Text(name) }
                  )
                }
              }
            FieldsRow(minItemWidth = 160.dp) {
              Field(
                interval,
                { interval = it.filter(Char::isDigit).take(2) },
                CalendarText.repeatEvery(),
                isError = intervalValue == null || intervalValue !in 1..99,
                number = true
              )
              DateTextField(
                until,
                { until = it },
                CalendarText.repeatUntil(),
                placeholder = CalendarText.optional(),
                isError = until.isNotBlank() && (untilDay == null || (startDay != null && untilDay < startDay))
              )
            }
          }
          Field(location, { location = it }, CalendarText.location())
        }
        BerillTextField(
          notes,
          { notes = it },
          label = { Text(CalendarText.notes(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          minLines = 2,
          modifier = Modifier.fillMaxWidth()
        )
        if (linked) {
          val name = calendars.firstOrNull { it.id == initial.calendarId }?.title ?: initial.calendarId.orEmpty()
          Text(
            "${CalendarText.calendar()}: $name",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else if (writable.isNotEmpty()) {
          DropdownField(
            CalendarText.calendar(),
            listOf(LOCAL) + writable.map { it.id },
            calendar,
            { id -> if (id == LOCAL) CalendarText.onlyBerill() else writable.firstOrNull { it.id == id }?.title ?: id },
            { calendar = it },
            Modifier.fillMaxWidth()
          )
        }
        if (!isNew)
          TextButton(onClick = { askDelete = true }) {
            Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(CalendarText.deleteEvent(), color = MaterialTheme.colorScheme.error)
          }
      }
    },
    confirmButton = { TextButton(enabled = valid, onClick = confirm) { Text(UiText.save()) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.cancel()) } }
  )
  if (askDelete) {
    if (initial.recurring && occurrence != null && !initial.isBirthday)
      AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text(CalendarText.deleteRepeatQuestion()) },
        text = { Text(CalendarText.deleteEventText()) },
        confirmButton = {
          Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = onSkip) { Text(CalendarText.deleteOccurrence(), color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDelete) { Text(CalendarText.deleteSeries(), color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { askDelete = false }) { Text(UiText.cancel()) }
          }
        }
      )
    else
      ConfirmDialog(
        CalendarText.deleteEventQuestion(),
        CalendarText.deleteEventText(),
        onConfirm = onDelete,
        onDismiss = { askDelete = false }
      )
  }
}
