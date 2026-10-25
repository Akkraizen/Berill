package dev.akkraizen.berill.core.plugin

import dev.akkraizen.berill.api.Navigator
import dev.akkraizen.berill.api.PluginContext
import kotlinx.coroutines.flow.StateFlow

internal class RouteScopedContext(
  private val base: PluginContext,
  route: StateFlow<String?>,
  page: StateFlow<String?>
) : PluginContext by base {
  override val nav: Navigator = object : Navigator by base.nav {
    override val route: StateFlow<String?> = route
    override val page: StateFlow<String?> = page
  }
}
