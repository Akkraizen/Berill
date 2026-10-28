package dev.akkraizen.berill.plugins.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.observeString
import dev.akkraizen.berill.api.observeStringList
import dev.akkraizen.berill.api.set
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.hhmm
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.localDateTime
import dev.akkraizen.berill.api.ui.navigation.rememberOpenLink
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.plugins.planner.data.GOOGLE_DEFAULT
import dev.akkraizen.berill.plugins.planner.data.GOOGLE_SELECTED
import dev.akkraizen.berill.plugins.planner.data.GoogleCalendarSync
import dev.akkraizen.berill.plugins.planner.i18n.CalendarText
import kotlinx.coroutines.launch

@Composable
internal fun GoogleSignInButton(google: GoogleCalendarSync, compact: Boolean = false) {
  if (!google.isConfigured()) return
  val state by google.signInState.collectAsState()
  val open = rememberOpenLink()
  if (state.running) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
      Spacer(Modifier.width(12.dp))
      Text(CalendarText.waitingBrowser(), style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.width(4.dp))
      TextButton(onClick = { google.cancelSignIn() }) { Text(UiText.cancel()) }
    }
    return
  }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (compact)
      OutlinedButton(onClick = { google.startSignIn { open(it) } }) {
        Ic(Icons.Outlined.Event, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(CalendarText.signIn())
      }
    else
      Button(onClick = { google.startSignIn { open(it) } }) { Text(CalendarText.signIn()) }
    state.error?.let {
      Text(CalendarText.signInError(it)(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
  }
}

@Composable
internal fun GoogleCalendarSettings(ctx: PluginContext) {
  val google = remember(ctx) { GoogleCalendarSync.of(ctx) }
  val connected by google.connected.collectAsState(google.isConnected())
  val account by google.account.collectAsState(null)
  val sync by google.state.collectAsState()
  val calendars by remember(google) { google.observeCalendars() }.collectAsState(google.calendars())
  val chosen by remember(ctx) { ctx.settings.observeStringList(GOOGLE_SELECTED) }.collectAsState(null)
  val defaultId by remember(ctx) { ctx.settings.observeString(GOOGLE_DEFAULT) }.collectAsState(null)
  val scope = rememberCoroutineScope()
  val muted = MaterialTheme.colorScheme.onSurfaceVariant

  SectionCard(CalendarText.google(), Icons.Outlined.Event) {
    if (!connected) {
      Text(
        if (google.isConfigured()) CalendarText.googleHint() else CalendarText.googleUnavailable(),
        style = MaterialTheme.typography.bodySmall,
        color = muted
      )
      GoogleSignInButton(google)
      return@SectionCard
    }
    Text(CalendarText.connectedAs(account ?: "Google")(), style = MaterialTheme.typography.bodyLarge)
    Text(
      when {
        sync.running -> CalendarText.syncing()
        sync.error != null -> CalendarText.syncError(sync.error.orEmpty())()
        sync.lastSync != null -> CalendarText.lastSync(localDateTime(sync.lastSync!!).hhmm())()
        else -> ""
      },
      style = MaterialTheme.typography.bodySmall,
      color = if (sync.error != null) MaterialTheme.colorScheme.error else muted
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(enabled = !sync.running, onClick = { scope.launch { runCatching { google.sync() } } }) { Text(CalendarText.syncNow()) }
      OutlinedButton(onClick = { scope.launch { google.signOut() } }) { Text(CalendarText.signOut()) }
    }
    if (calendars.isNotEmpty()) {
      Text(CalendarText.showCalendars(), style = MaterialTheme.typography.titleSmall)
      val selected = chosen ?: google.selected()
      Column {
        calendars.forEach { cal ->
          Row(
            Modifier.fillMaxWidth().interactive {
              val next = if (cal.id in selected) selected - cal.id else selected + cal.id
              google.setSelected(next)
              google.scheduleSync(0)
            },
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(checked = cal.id in selected, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(cal.color?.let(::parseHex) ?: MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(cal.title, Modifier.weight(1f))
            if (!cal.writable) Text(CalendarText.readOnly(), style = MaterialTheme.typography.bodySmall, color = muted)
          }
        }
      }
      val writable = calendars.filter { it.writable }
      if (writable.isNotEmpty()) {
        val current = writable.firstOrNull { it.id == defaultId }?.id ?: google.defaultCalendar()
        DropdownField(
          CalendarText.defaultCalendar(),
          writable.map { it.id },
          current,
          { id -> writable.firstOrNull { it.id == id }?.title ?: id },
          { google.setDefault(it) },
          Modifier.fillMaxWidth()
        )
      }
    }
  }
}
