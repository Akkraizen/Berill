package dev.akkraizen.berill.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
internal expect fun platformDynamicColorScheme(dark: Boolean): ColorScheme?

internal expect val supportsDynamicColor: Boolean
