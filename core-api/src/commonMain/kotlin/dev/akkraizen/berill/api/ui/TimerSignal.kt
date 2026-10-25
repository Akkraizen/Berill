package dev.akkraizen.berill.api.ui

import androidx.compose.runtime.Composable

@Composable
expect fun rememberTimerSignal(): () -> Unit
