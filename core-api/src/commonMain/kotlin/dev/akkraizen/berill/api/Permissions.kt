package dev.akkraizen.berill.api

class PluginPermissions(val network: List<String> = emptyList(), val anyImageHost: Boolean = false) {
  fun allowsRequest(url: String): Boolean {
    val parsed = parseWebUrl(url) ?: return false
    return parsed.scheme == "https" && network.any { hostMatches(parsed.host, it) }
  }

  fun allowsImage(url: String): Boolean {
    val parsed = parseWebUrl(url) ?: return false
    return anyImageHost ||
      (parsed.scheme == "https" && network.any { hostMatches(parsed.host, it) })
  }

  companion object {
    val None = PluginPermissions()
  }
}

class NetworkNotAllowedException(val host: String) : Exception("Network access to $host is not declared by the plugin")

data class WebUrl(val scheme: String, val host: String)

fun parseWebUrl(url: String): WebUrl? {
  val u = url.trim()
  val scheme = u.substringBefore("://", "").lowercase()
  if (scheme != "http" && scheme != "https") return null
  val authority = u.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
  if (authority.isEmpty() || '@' in authority || authority.any { it.isWhitespace() }) return null
  val host = (
    if (authority.startsWith("["))
      authority.substringBefore(']') + "]"
    else
      authority.substringBefore(':')
  )
    .lowercase()
  return if (host.isEmpty()) null else WebUrl(scheme, host)
}

fun hostMatches(host: String, pattern: String): Boolean {
  val p = pattern.trim().lowercase()
  return if (p.startsWith("*."))
    host.endsWith(p.substring(1)) && host.length > p.length - 1
  else
    host == p
}
