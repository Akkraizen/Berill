package dev.akkraizen.berill.api.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

@Composable
actual fun rememberTimerSignal(): () -> Unit {
  val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() }
  DisposableEffect(tone) { onDispose { tone?.release() } }
  return remember(tone) { { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400); Unit } }
}
