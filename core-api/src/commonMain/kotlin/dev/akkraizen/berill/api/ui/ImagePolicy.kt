package dev.akkraizen.berill.api.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.akkraizen.berill.api.parseWebUrl

val LocalImagePolicy = staticCompositionLocalOf<(String) -> Boolean> {
  { url ->
    parseWebUrl(url) != null
  }
}
