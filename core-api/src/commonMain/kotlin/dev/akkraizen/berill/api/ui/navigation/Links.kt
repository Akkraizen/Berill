package dev.akkraizen.berill.api.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

private val SAFE_SCHEMES = setOf("http", "https", "mailto")

fun isSafeExternalLink(url: String): Boolean {
  val u = url.trim()
  val scheme = u.substringBefore(':', "").lowercase()
  if (scheme !in SAFE_SCHEMES) return false
  if (u.any { it.isWhitespace() || it.code < 0x20 }) return false
  return when (scheme) {
    "mailto" -> u.length > "mailto:".length
    else -> {
      val rest = u.substringAfter("://", "")
      val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
      authority.isNotEmpty() && '@' !in authority
    }
  }
}

@Composable
fun rememberOpenLink(): (String) -> Boolean {
  val handler = LocalUriHandler.current
  return remember(handler) {
    { url -> isSafeExternalLink(url) && runCatching { handler.openUri(url.trim()) }.isSuccess }
  }
}
