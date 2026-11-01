package dev.akkraizen.berill.core.connections

import dev.akkraizen.berill.api.ui.interaction.*
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.log.AppLog
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class ShortcutSettings(private val store: LocalStore) {
  private val json = Json { ignoreUnknownKeys = true }
  private val _state = MutableStateFlow(read())
  val state = _state.asStateFlow()
  private fun read(): ShortcutProfile = try {
    val loaded = store.meta(KEY)?.let { json.decodeFromString<ShortcutProfile>(it) } ?: ShortcutProfile()
    val focus = ShortcutAction.FOCUS
    val profile = if (!loaded.overrides.containsKey(focus) && loaded.conflict(focus, focus.defaultBinding!!) != null)
      loaded.copy(overrides = loaded.overrides + (focus to null)) else loaded
    require(ShortcutAction.entries.all { action -> profile.binding(action)?.let { it.valid && profile.conflict(action, it) == null } ?: true })
    profile
  } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { AppLog.warn("shortcuts", "invalid saved profile; using defaults", e); ShortcutProfile() }
  fun set(action: ShortcutAction, chord: ShortcutChord?) {
    require(chord == null || chord.valid && _state.value.conflict(action, chord) == null)
    save(_state.value.copy(overrides = _state.value.overrides + (action to chord)))
  }
  fun reset(action: ShortcutAction) {
    val next = _state.value.copy(overrides = _state.value.overrides - action)
    require(next.binding(action)?.let { next.conflict(action, it) == null } ?: true)
    save(next)
  }
  fun resetAll() = save(ShortcutProfile())
  private fun save(profile: ShortcutProfile) {
    store.setMeta(KEY, json.encodeToString(profile))
    _state.value = profile
  }
  private companion object { const val KEY = "shortcuts.profile" }
}

fun BerillCore.handleShortcut(event: KeyEvent): Boolean {
  if (ShortcutCapture.active || event.type != KeyEventType.KeyDown || !onboarded.value) return false
  val profile = shortcuts.state.value
  val action = ShortcutAction.entries.firstOrNull { !it.editorOnly && profile.matches(it, event) } ?: return false
  if (action == ShortcutAction.FOCUS && nav.stack.value.last() !is dev.akkraizen.berill.core.nav.Destination.Note) return false
  connections.errors.run(FailureOperation.COMMAND) {
    when (action) {
      ShortcutAction.FOCUS -> {
        val note = nav.stack.value.last() as dev.akkraizen.berill.core.nav.Destination.Note
        focusedNote.value = if (focusedNote.value == note.id) null else note.id
      }
      ShortcutAction.SEARCH -> connections.searchOpen.value = true
      ShortcutAction.DAY -> connections.dayOpen.value = true
      ShortcutAction.TRASH -> connections.trashOpen.value = true
      ShortcutAction.NEW_NOTE -> nav.openNote(null)
      ShortcutAction.SETTINGS -> nav.openSettings("desktop")
      else -> Unit
    }
  }
  return true
}
