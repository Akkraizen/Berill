package dev.akkraizen.berill.core.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.hhmm
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.localDateTime
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.nav.Tab
import dev.akkraizen.berill.core.sync.SyncOptions
import dev.akkraizen.berill.core.sync.SyncState
import dev.akkraizen.berill.core.ui.CoreText
import dev.akkraizen.berill.protocol.Collections
import kotlinx.coroutines.launch

@Composable
internal fun SyncSettings(core: BerillCore) {
  val sync = core.sync
  val state by sync.state.collectAsState()
  var url by remember { mutableStateOf(sync.serverUrl() ?: "https://") }
  var token by remember { mutableStateOf(sync.token() ?: "") }
  val scope = rememberCoroutineScope()

  BerillTextField(
    url,
    { url = it },
    label = { Text(CoreText.serverUrl()) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth()
  )
  BerillTextField(
    token,
    { token = it },
    label = { Text(CoreText.accessToken()) },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    modifier = Modifier.fillMaxWidth()
  )
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = { sync.configure(url, token) }) { Text(CoreText.save()) }
    FilledTonalButton(onClick = { scope.launch { sync.syncNow() } }) { Text(CoreText.syncNow()) }
  }
  Text(
    when (val s = state) {
      SyncState.NotConfigured -> CoreText.syncNotConfigured()
      SyncState.Idle -> CoreText.syncIdle()
      SyncState.Syncing -> CoreText.syncInProgress()
      is SyncState.Done -> CoreText.syncDone(localDateTime(s.atMillis).hhmm(), s.sent, s.received)()

      is SyncState.Failed -> CoreText.syncFailed(s.message)()
    },
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
  SyncDetails(core)
  SyncOptionsSettings(core)
  EncryptionSettings(core)
}

@Composable
private fun SyncOptionsSettings(core: BerillCore) {
  val sync = core.sync
  val options by sync.options.collectAsState()
  fun update(change: SyncOptions.() -> SyncOptions) = sync.setOptions(options.change())

  HorizontalDivider(Modifier.padding(vertical = 4.dp))
  SettingSwitch(CoreText.autoSync(), CoreText.autoSyncHint(), options.auto) {
    update { copy(auto = it) }
  }
  AnimatedVisibility(options.auto) {
    ChoiceChips(
      options = SyncOptions.INTERVALS,
      selected = options.intervalMinutes,
      label = { if (it < 60) CoreText.everyMinutes(it)() else CoreText.everyHour() },
      onSelect = { minutes -> update { copy(intervalMinutes = minutes) } }
    )
  }

  SettingsSubtitle(CoreText.settings())
  SettingSwitch(CoreText.syncAppearance(), CoreText.syncAppearanceHint(), options.appearance) {
    update { copy(appearance = it) }
  }
  SettingSwitch(CoreText.syncSections(), CoreText.syncSectionsHint(), options.sections) {
    update { copy(sections = it) }
  }
  SettingSwitch(
    CoreText.syncPluginSettings(),
    CoreText.syncPluginSettingsHint(),
    options.pluginSettings
  ) {
    update { copy(pluginSettings = it) }
  }

  SettingsSubtitle(CoreText.data())

  val installed by core.plugins.installed.collectAsState()
  val enabled by core.plugins.enabled.collectAsState()
  val sections = remember(installed, enabled, I18n.language) {
    listOf(
      DataSection(
        Tab.Notes.title,
        Tab.Notes.icon,
        listOf(Collections.NOTES),
        enabled = true
      )
    ) +
      installed.mapNotNull { p ->
        core.collections
          .of(p)
          .takeIf { it.isNotEmpty() }
          ?.let { DataSection(p.title, p.icon, it, enabled = p in enabled) }
      }
  }
  sections.forEach { section ->
    val collections = section.collections
    val on = collections.none { it in options.excludedCollections }
    val note = listOfNotNull(
      CoreText.onlyOnThisDevice().takeUnless { on },
      CoreText.sectionDisabled().takeUnless { section.enabled }
    )
      .joinToString(" · ")
      .ifEmpty { null }
    SettingSwitch(section.title, note, on, section.icon) { enable ->
      update {
        copy(
          excludedCollections = if (enable)
            excludedCollections - collections.toSet()
          else
            excludedCollections + collections
        )
      }
    }
  }
  Text(
    CoreText.syncDataHint(),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}
