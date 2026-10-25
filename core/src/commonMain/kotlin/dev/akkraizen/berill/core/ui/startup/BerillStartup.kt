package dev.akkraizen.berill.core.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.ui.brand.MosaicLogo
import dev.akkraizen.berill.core.ui.shell.BerillShell
import dev.akkraizen.berill.core.ui.theme.BerillTheme
import kotlinx.coroutines.CancellationException

@Composable
fun BerillStartup(
  load: suspend () -> BerillCore,
  windowChrome: (@Composable (String) -> Unit)? = null,
  overlay: @Composable () -> Unit = {},
  onReady: (BerillCore) -> Unit = {}
) {
  var core by remember { mutableStateOf<BerillCore?>(null) }
  var failed by remember { mutableStateOf(false) }
  var attempt by remember { mutableIntStateOf(0) }
  val initialize by rememberUpdatedState(load)
  val ready by rememberUpdatedState(onReady)

  LaunchedEffect(attempt) {
    failed = false
    withFrameNanos { }
    try {
      val initialized = initialize()
      ready(initialized)
      core = initialized
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      failed = true
    }
  }

  val initialized = core
  if (initialized != null) {
    BerillShell(initialized, windowChrome, overlay)
  } else {
    BerillTheme {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
          windowChrome?.invoke("Berill")
          Box(Modifier.fillMaxWidth().weight(1f).safeDrawingPadding().padding(24.dp),
            contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 360.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(20.dp)) {
              MosaicLogo(108.dp)
              Text("Berill", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface)
              Text(I18n.text(if (failed) "startup.failed" else "startup.loading"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
              if (failed) {
                Button(onClick = { attempt++ }) { Text(I18n.text("startup.retry")) }
              } else {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
              }
            }
          }
        }
      }
    }
  }
}
