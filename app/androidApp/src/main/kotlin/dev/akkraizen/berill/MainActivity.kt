package dev.akkraizen.berill

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
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.files.DocumentPicker
import dev.akkraizen.berill.core.files.ImagePicker
import dev.akkraizen.berill.core.ui.startup.BerillStartup
import dev.akkraizen.berill.core.ui.theme.Appearance
import dev.akkraizen.berill.core.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.Theme_Berill)
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    ImagePicker.register(this)
    DocumentPicker.register(this)
    val app = application as BerillApplication
    setContent {
      var core by remember { mutableStateOf<BerillCore?>(null) }
      val appearance = core?.appearance?.state?.collectAsState()?.value ?: Appearance()
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
        load = { withContext(Dispatchers.IO) { app.core } },
        onReady = { core = it },
        overlay = { ImagePicker.Content() }
      )
    }
  }
}
