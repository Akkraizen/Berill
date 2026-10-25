package dev.akkraizen.berill.api.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit

@Composable
actual fun rememberTimerSignal(): () -> Unit = remember {
  val signal: () -> Unit = { runCatching { Toolkit.getDefaultToolkit().beep() } }
  signal
}
