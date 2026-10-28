package dev.akkraizen.berill.core.ui.theme

import androidx.compose.ui.graphics.Color
import dev.akkraizen.berill.api.i18n.AppLanguage
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.ui.CoreText
import dev.akkraizen.berill.protocol.Collections
import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class ThemeMode(private val tr: Tr) {
  System(CoreText.themeSystem),
  Light(CoreText.themeLight),
  Dark(CoreText.themeDark),
  ;

  val title: String
    get() = tr()
}

data class Appearance(
  val mode: ThemeMode = ThemeMode.System,
  val accent: String = ACCENT_DEFAULT,
  val language: String = LANGUAGE_SYSTEM
) {

  val appLanguage: AppLanguage
    get() = AppLanguage.fromCode(language) ?: AppLanguage.system()

  companion object {
    const val LANGUAGE_SYSTEM = "system"
    const val ACCENT_DEFAULT = "default"
    const val ACCENT_DYNAMIC = "dynamic"
  }
}

val ACCENT_PRESETS: List<Pair<Tr, String>> = listOf(
  CoreText.accentGreen to Appearance.ACCENT_DEFAULT,
  CoreText.accentEmerald to "#00897B",
  CoreText.accentTeal to "#00838F",
  CoreText.accentBlue to "#1E63D6",
  CoreText.accentIndigo to "#3F51B5",
  CoreText.accentViolet to "#7E57C2",
  CoreText.accentPink to "#D81B60",
  CoreText.accentRed to "#D32F2F",
  CoreText.accentOrange to "#EF6C00",
  CoreText.accentAmber to "#F9A825",
  CoreText.accentGraphite to "#546E7A"
)

fun accentSwatch(accent: String): Color? =
  when (accent) {
    Appearance.ACCENT_DEFAULT -> Color(0xFF2E6B30)
    Appearance.ACCENT_DYNAMIC -> null
    else -> parseHexColor(accent)
  }

fun parseHexColor(text: String): Color? {
  val hex = text.trim().removePrefix("#")
  if (hex.length != 6 || hex.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
  return Color(0xFF000000 or hex.toLong(16))
}

interface AppearanceCache {
  fun read(): Appearance?

  fun write(appearance: Appearance)
}

fun Appearance.encode(): String = listOf(mode.name, accent, language).joinToString("\n")

fun decodeAppearance(text: String?): Appearance? {
  val parts = text?.lines()?.map { it.trim() } ?: return null
  if (parts.size < 3) return null
  val mode = ThemeMode.entries.firstOrNull { it.name == parts[0] } ?: return null
  return Appearance(
    mode,
    parts[1].ifEmpty { Appearance.ACCENT_DEFAULT },
    parts[2].ifEmpty { Appearance.LANGUAGE_SYSTEM }
  )
}

class AppearanceStore(private val store: LocalStore, scope: CoroutineScope) {
  val state: StateFlow<Appearance> = store
    .observe(Collections.SETTINGS)
    .map { records -> parse(records.firstOrNull { it.id == RECORD }) }
    .stateIn(scope, SharingStarted.Eagerly, parse(store.get(Collections.SETTINGS, RECORD)?.takeUnless { it.deleted }))

  fun setMode(mode: ThemeMode) = store.put(Collections.SETTINGS, RECORD, mapOf("themeMode" to JsonPrimitive(mode.name)))

  fun setAccent(accent: String) = store.put(Collections.SETTINGS, RECORD, mapOf("accent" to JsonPrimitive(accent)))

  fun setLanguage(language: String) = store.put(Collections.SETTINGS, RECORD, mapOf("language" to JsonPrimitive(language)))

  private fun parse(record: SyncRecord?): Appearance {
    val fields = record?.fields.orEmpty()
    val mode = (fields["themeMode"] as? JsonPrimitive)?.contentOrNull?.let { name ->
      ThemeMode.entries.firstOrNull { it.name == name }
    } ?: ThemeMode.System
    val accent = (fields["accent"] as? JsonPrimitive)?.contentOrNull ?: Appearance.ACCENT_DEFAULT
    val language = (fields["language"] as? JsonPrimitive)?.contentOrNull ?: Appearance.LANGUAGE_SYSTEM
    return Appearance(mode, accent, language)
  }

  private companion object {
    const val RECORD = "appearance"
  }
}
