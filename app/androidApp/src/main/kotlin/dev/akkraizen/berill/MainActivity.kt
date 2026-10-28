package dev.akkraizen.berill

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
    val app = application as BerillApplication
    if (savedInstanceState == null) WidgetHost.handleIntent(this, intent, app.scope)
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

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    WidgetHost.handleIntent(this, intent, (application as BerillApplication).scope)
  }

  private companion object {
    const val DARK_BACKGROUND = 0xFF10140F.toInt()
    const val LIGHT_BACKGROUND = 0xFFF7FBF1.toInt()
  }
}
