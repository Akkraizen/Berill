package dev.akkraizen.berill.core.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build

class PreferencesAppearanceCache(context: Context) : AppearanceCache {
  private val app = context.applicationContext
  private val prefs = app.getSharedPreferences("appearance", Context.MODE_PRIVATE)

  override fun read(): Appearance? = decodeAppearance(prefs.getString(KEY, null))

  override fun write(appearance: Appearance) {
    val previous = read()
    val text = appearance.encode()
    if (prefs.getString(KEY, null) != text) prefs.edit().putString(KEY, text).apply()
    if (previous?.mode != appearance.mode) applyNightMode(appearance.mode)
  }

  private fun applyNightMode(mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val manager = app.getSystemService(UiModeManager::class.java) ?: return
    val night = when (mode) {
      ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
      ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
      ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
    }
    runCatching { manager.setApplicationNightMode(night) }
  }

  private companion object {
    const val KEY = "appearance"
  }
}

fun cachedAndroidAppearance(context: Context): Appearance = PreferencesAppearanceCache(context).read() ?: Appearance()
