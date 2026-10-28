package dev.akkraizen.berill.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.akkraizen.berill.api.HttpAccess
import dev.akkraizen.berill.api.HttpRequest
import dev.akkraizen.berill.api.HttpResponse
import dev.akkraizen.berill.api.NetworkNotAllowedException
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.parseWebUrl
import dev.akkraizen.berill.api.ui.LocalImagePolicy
import kotlinx.serialization.json.JsonElement

internal class GuardedHttpAccess(
  private val inner: HttpAccess,
  private val permissions: PluginPermissions
) : HttpAccess {
  override suspend fun getJson(url: String, headers: Map<String, String>): JsonElement {
    if (!permissions.allowsRequest(url))
      throw NetworkNotAllowedException(parseWebUrl(url)?.host ?: url.take(60))
    return inner.getJson(url, headers)
  }

  override suspend fun send(request: HttpRequest): HttpResponse {
    if (!permissions.allowsRequest(request.url))
      throw NetworkNotAllowedException(parseWebUrl(request.url)?.host ?: request.url.take(60))
    return inner.send(request)
  }
}

@Composable
internal fun PluginScope(plugin: BerillPlugin, content: @Composable () -> Unit) {
  val policy = remember(plugin.id) {
    val p = plugin.permissions;
    { url: String -> p.allowsImage(url) }
  }
  CompositionLocalProvider(LocalImagePolicy provides policy, content = content)
}
