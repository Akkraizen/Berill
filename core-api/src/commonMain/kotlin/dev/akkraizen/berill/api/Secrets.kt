package dev.akkraizen.berill.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

interface SecretStore {
  fun get(key: String): String?

  fun set(key: String, value: String?)

  fun observe(key: String): Flow<String?>
}

object NoSecrets : SecretStore {
  override fun get(key: String): String? = null

  override fun set(key: String, value: String?) = Unit

  override fun observe(key: String): Flow<String?> = flowOf(null)
}

fun PluginContext.migrateSecretFromSettings(key: String) {
  val old = (settings.get(key) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
  if (old.isEmpty()) return
  if (secrets.get(key).isNullOrEmpty()) secrets.set(key, old)
  settings.set(key, JsonPrimitive(""))
}
