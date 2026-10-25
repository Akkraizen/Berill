package dev.akkraizen.berill.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null

internal actual val supportsDynamicColor: Boolean = false
