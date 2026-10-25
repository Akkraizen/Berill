package dev.akkraizen.berill.api

import kotlinx.coroutines.CoroutineScope

interface PluginContext {
  val pluginId: String

  val data: DataAccess

  val nav: Navigator

  val capabilities: CapabilityLookup

  val settings: PluginSettings

  val http: HttpAccess

  val files: FileAccess

  val scope: CoroutineScope

  val secrets: SecretStore
    get() = NoSecrets
}
