package dev.akkraizen.berill.api.ui.interaction

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import kotlinx.serialization.Serializable

@Serializable
enum class ShortcutKey(val label: String, val composeKey: Key) {
  A("A", Key.A),
  B("B", Key.B),
  C("C", Key.C),
  D("D", Key.D),
  E("E", Key.E),
  F("F", Key.F),
  G("G", Key.G),
  H("H", Key.H),
  I("I", Key.I),
  J("J", Key.J),
  K("K", Key.K),
  L("L", Key.L),
  M("M", Key.M),
  N("N", Key.N),
  O("O", Key.O),
  P("P", Key.P),
  Q("Q", Key.Q),
  R("R", Key.R),
  S("S", Key.S),
  T("T", Key.T),
  U("U", Key.U),
  V("V", Key.V),
  W("W", Key.W),
  X("X", Key.X),
  Y("Y", Key.Y),
  Z("Z", Key.Z),
  D0("0", Key.Zero),
  D1("1", Key.One),
  D2("2", Key.Two),
  D3("3", Key.Three),
  D4("4", Key.Four),
  D5("5", Key.Five),
  D6("6", Key.Six),
  D7("7", Key.Seven),
  D8("8", Key.Eight),
  D9("9", Key.Nine),
  F1("F1", Key.F1),
  F2("F2", Key.F2),
  F3("F3", Key.F3),
  F4("F4", Key.F4),
  F5("F5", Key.F5),
  F6("F6", Key.F6),
  F7("F7", Key.F7),
  F8("F8", Key.F8),
  F9("F9", Key.F9),
  F10("F10", Key.F10),
  F11("F11", Key.F11),
  F12("F12", Key.F12),
  SPACE("Space", Key.Spacebar),
  COMMA(",", Key.Comma),
  ENTER("Enter", Key.Enter);
  companion object { fun from(key: Key): ShortcutKey? = entries.firstOrNull { it.composeKey == key } }
}

@Serializable
data class ShortcutChord(val key: ShortcutKey, val ctrl: Boolean = false, val alt: Boolean = false,
  val shift: Boolean = false, val meta: Boolean = false, val primary: Boolean = false) {
  val valid: Boolean get() = !(primary && (ctrl || meta)) && (ctrl || alt || meta || primary || key.name.startsWith("F") && key.name.drop(1).toIntOrNull() != null)
  fun label(): String = buildList {
    if (primary) add("Ctrl/Cmd")
    if (ctrl) add("Ctrl")
    if (meta) add("Cmd")
    if (alt) add("Alt")
    if (shift) add("Shift")
    add(key.label)
  }.joinToString("+")
  fun matches(key: ShortcutKey, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean): Boolean =
    this.key == key && this.alt == alt && this.shift == shift &&
      if (primary) (ctrl != meta) else this.ctrl == ctrl && this.meta == meta
  fun matches(event: KeyEvent): Boolean = event.type == KeyEventType.KeyDown &&
    ShortcutKey.from(event.key)?.let { matches(it, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed, event.isMetaPressed) } == true
  fun overlaps(other: ShortcutChord): Boolean = key == other.key && alt == other.alt && shift == other.shift &&
    listOf(false, true).any { c -> listOf(false, true).any { m -> matches(key, c, alt, shift, m) && other.matches(key, c, alt, shift, m) } }
  companion object {
    fun capture(event: KeyEvent): ShortcutChord? = ShortcutKey.from(event.key)?.let {
      ShortcutChord(it, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed, event.isMetaPressed)
    }
    fun parseLabel(label: String): ShortcutChord? {
      val parts = label.split('+')
      val key = ShortcutKey.entries.firstOrNull { it.label.equals(parts.last(), true) } ?: return null
      val mods = parts.dropLast(1).map { it.lowercase() }
      if (mods.any { it !in listOf("ctrl", "cmd", "alt", "option", "shift", "ctrl/cmd") }) return null
      return ShortcutChord(key, "ctrl" in mods, "alt" in mods || "option" in mods, "shift" in mods, "cmd" in mods, "ctrl/cmd" in mods).takeIf { it.valid }
    }
  }
}

@Serializable
enum class ShortcutAction(val editorOnly: Boolean = false) {
  SEARCH, DAY, TRASH, NEW_NOTE, SETTINGS, FOCUS, BOLD(true), ITALIC(true);
  val defaultBinding: ShortcutChord? get() = when (this) {
    FOCUS -> ShortcutChord(ShortcutKey.F, shift = true, primary = true)
    SEARCH -> ShortcutChord(ShortcutKey.K, primary = true)
    NEW_NOTE -> ShortcutChord(ShortcutKey.N, primary = true)
    SETTINGS -> ShortcutChord(ShortcutKey.COMMA, primary = true)
    BOLD -> ShortcutChord(ShortcutKey.B, primary = true)
    ITALIC -> ShortcutChord(ShortcutKey.I, primary = true)
    DAY, TRASH -> null
  }
}

@Serializable
data class ShortcutProfile(val overrides: Map<ShortcutAction, ShortcutChord?> = emptyMap()) {
  fun binding(action: ShortcutAction): ShortcutChord? = if (overrides.containsKey(action)) overrides[action] else action.defaultBinding
  fun conflict(action: ShortcutAction, chord: ShortcutChord): ShortcutAction? = ShortcutAction.entries.firstOrNull {
    it != action && binding(it)?.overlaps(chord) == true
  }
  fun matches(action: ShortcutAction, event: KeyEvent): Boolean = binding(action)?.matches(event) == true
  fun label(action: ShortcutAction): String? = binding(action)?.label()
}

val LocalShortcuts = staticCompositionLocalOf { ShortcutProfile() }
object ShortcutCapture { var active: Boolean by mutableStateOf(false) }
