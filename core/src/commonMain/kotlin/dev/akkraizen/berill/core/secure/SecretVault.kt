package dev.akkraizen.berill.core.secure

import dev.akkraizen.berill.api.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface SecretVault {
  fun read(name: String): String?

  fun write(name: String, value: String?)
}

class MemoryVault : SecretVault {
  private val values = HashMap<String, String>()

  override fun read(name: String): String? = values[name]

  override fun write(name: String, value: String?) {
    if (value.isNullOrEmpty()) values.remove(name) else values[name] = value
  }
}

internal class PluginSecrets(private val vault: SecretVault, private val pluginId: String) : SecretStore {
  private val version = MutableStateFlow(0)

  private fun name(key: String): String {
    require(Regex("[A-Za-z0-9_.-]{1,64}").matches(key)) { "bad secret key" }
    return "plugin.$pluginId.$key"
  }

  override fun get(key: String): String? = runCatching { vault.read(name(key)) }.getOrNull()

  override fun set(key: String, value: String?) {
    vault.write(name(key), value?.trim()?.takeIf { it.isNotEmpty() })
    version.value++
  }

  override fun observe(key: String): Flow<String?> = version.map { get(key) }.distinctUntilChanged()
}
