package dev.akkraizen.berill

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.files.DocumentPicker
import dev.akkraizen.berill.core.files.ImagePicker
import dev.akkraizen.berill.core.ui.startup.BerillStartup
import dev.akkraizen.berill.core.ui.theme.ThemeMode
import dev.akkraizen.berill.core.ui.theme.cachedAndroidAppearance
import dev.akkraizen.berill.widgets.WidgetHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.Theme_Berill)
    val cached = cachedAndroidAppearance(this)
    I18n.language = cached.appLanguage
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && cached.mode != ThemeMode.System)
      window.setBackgroundDrawable(ColorDrawable(if (cached.mode == ThemeMode.Dark) DARK_BACKGROUND else LIGHT_BACKGROUND))
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    ImagePicker.register(this)
    DocumentPicker.register(this)
    HealthPermissions.register(this)
    dev.akkraizen.berill.core.connections.ReminderPermission.register(this)
    val app = application as BerillApplication
    if (savedInstanceState == null) WidgetHost.handleIntent(this, intent, app.scope)
    if (savedInstanceState == null) handleCapture(intent)
    setContent {
      var core by remember { mutableStateOf<BerillCore?>(null) }
      val appearance = core?.appearance?.state?.collectAsState()?.value ?: cached
      val dark = when (appearance.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
      }
      val view = LocalView.current
      SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
          isAppearanceLightStatusBars = !dark
          isAppearanceLightNavigationBars = !dark
        }
      }
      BerillStartup(
        initialAppearance = cached,
        load = { withContext(Dispatchers.IO) { app.core } },
        onReady = { core = it },
        overlay = { ImagePicker.Content() }
      )
    }
  }

  @SuppressLint("RestrictedApi")
  override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
    if (event.action == android.view.KeyEvent.ACTION_DOWN && event.keyCode == android.view.KeyEvent.KEYCODE_K && (event.isCtrlPressed || event.isMetaPressed)) {
      val app = application as BerillApplication
      app.scope.launch { app.core.connections.searchOpen.value = true }
      return true
    }
    return super.dispatchKeyEvent(event)
  }

  override fun onResume() {
    super.onResume()
    val app = application as BerillApplication
    app.scope.launch { app.core.healthImport?.refresh() }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleCapture(intent)
    WidgetHost.handleIntent(this, intent, (application as BerillApplication).scope)
  }

  private fun handleCapture(intent: Intent) {
    val app = application as BerillApplication
    intent.getStringExtra("berill.reminder.action")?.let { encoded ->
      intent.removeExtra("berill.reminder.action")
      app.scope.launch {
        val core = app.core
        core.widgets.decodeAction(encoded)?.let {
          core.widgets.perform(
            dev.akkraizen.berill.core.widgets.WidgetInstance("core.today"),
            it
          )
        }
      }
    }
    if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
      val text =
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(65536)?.trim().orEmpty()
      val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.take(300).orEmpty()
      if (text.isNotBlank()) app.scope.launch {
        app.core.connections.shared.value = dev.akkraizen.berill.api.SharedText(title, text)
      }
      intent.removeExtra(Intent.EXTRA_TEXT)
    }
    intent.getStringExtra("berill.quick")?.let { action ->
      intent.removeExtra("berill.quick")
      app.scope.launch {
        val core = app.core
        when (action) {
          "water" -> {
            val instance = dev.akkraizen.berill.core.widgets.WidgetInstance("nutrition.water")
            val content = core.widgets.content(instance)
            val button =
              content.blocks.filterIsInstance<dev.akkraizen.berill.api.widgets.WidgetBlock.Buttons>()
                .flatMap { it.items }.firstOrNull()
            if (button != null) core.widgets.perform(instance, button.action)
          }

          "expense" -> core.nav.showPlugin("finance", "add:")
          else -> core.connections.searchOpen.value = true
        }
      }
    }
  }

  private companion object {
    const val DARK_BACKGROUND = 0xFF10140F.toInt()
    const val LIGHT_BACKGROUND = 0xFFF7FBF1.toInt()
  }
}
