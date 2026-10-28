package dev.akkraizen.berill.plugins.planner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.planner.data.CalendarEvent
import dev.akkraizen.berill.plugins.planner.data.GoogleCalendarInfo
import dev.akkraizen.berill.plugins.planner.data.hhmm
import dev.akkraizen.berill.plugins.planner.data.parseTime
import dev.akkraizen.berill.plugins.planner.i18n.CalendarText
import kotlinx.datetime.LocalDateTime

private const val LOCAL = ""

@Composable
internal fun EventDialog(
  initial: CalendarEvent,
  isNew: Boolean,
  calendars: List<GoogleCalendarInfo>,
  defaultCalendar: String?,
  onDismiss: () -> Unit,
  onSave: (CalendarEvent) -> Unit,
  onDelete: () -> Unit
) {
  var title by remember { mutableStateOf(initial.title) }
  var allDay by remember { mutableStateOf(initial.allDay) }
  var date by remember { mutableStateOf(initial.date) }
  var endDate by remember { mutableStateOf(initial.endDate ?: initial.date) }
  var start by remember { mutableStateOf(initial.start ?: "09:00") }
  var end by remember { mutableStateOf(initial.end ?: "10:00") }
  var location by remember { mutableStateOf(initial.location) }
  var notes by remember { mutableStateOf(initial.notes) }
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
  val valid = title.isNotBlank() && startDay != null && lastDay != null &&
    (allDay || (startT != null && endT != null)) && ordered

  val confirm = {
    if (valid) {
      val d = startDay
      val last = lastDay
      onSave(
        initial.copy(
          title = title.trim(),
          date = d.toString(),
          endDate = last.takeIf { it != d }?.toString(),
          start = if (allDay) null else startT!!.hhmm(),
          end = if (allDay) null else endT!!.hhmm(),
          location = location.trim(),
          notes = notes.trim(),
          calendarId = if (linked) initial.calendarId else calendar.takeIf { it != LOCAL }
        )
      )
    }
  }

  DialogKeys(onConfirm = confirm, confirmEnabled = valid, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (isNew) CalendarText.newEvent() else CalendarText.event()) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(
          title,
          { title = it },
          label = { Text(CalendarText.eventTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(CalendarText.allDay(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
          Switch(checked = allDay, onCheckedChange = { allDay = it })
        }
        FieldsRow {
          BerillTextField(
            date,
            {
              val before = endDate == date
              date = it
              if (before) endDate = it
            },
            label = { Text(CalendarText.startDate(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = { Text(UiText.datePlaceholder()) },
            singleLine = true,
            isError = startDay == null,
            modifier = Modifier.fillMaxWidth()
          )
          BerillTextField(
            endDate,
            { endDate = it },
            label = { Text(CalendarText.endDate(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = { Text(UiText.datePlaceholder()) },
            singleLine = true,
            isError = lastDay == null || (!ordered && allDay),
            modifier = Modifier.fillMaxWidth()
          )
        }
        if (!allDay)
          FieldsRow {
            BerillTextField(
              start,
              { start = it },
              label = { Text(CalendarText.startTime(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
              placeholder = { Text(CalendarText.timePlaceholder()) },
              singleLine = true,
              isError = startT == null,
              modifier = Modifier.fillMaxWidth()
            )
            BerillTextField(
              end,
              { end = it },
              label = { Text(CalendarText.endTime(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
              placeholder = { Text(CalendarText.timePlaceholder()) },
              singleLine = true,
              isError = endT == null || !ordered,
              supportingText = if (!ordered) {
                { Text(CalendarText.endBeforeStart()) }
              } else
                null,
              modifier = Modifier.fillMaxWidth()
            )
          }
        BerillTextField(
          location,
          { location = it },
          label = { Text(CalendarText.location(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
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
  if (askDelete)
    ConfirmDialog(
      CalendarText.deleteEventQuestion(),
      CalendarText.deleteEventText(),
      onConfirm = onDelete,
      onDismiss = { askDelete = false }
    )
}
