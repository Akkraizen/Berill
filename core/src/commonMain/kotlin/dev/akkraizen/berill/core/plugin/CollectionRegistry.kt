package dev.akkraizen.berill.core.plugin

import dev.akkraizen.berill.api.DataAccess
import dev.akkraizen.berill.api.DataCollection
import dev.akkraizen.berill.api.RawCollection
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.protocol.Collections
import kotlinx.serialization.KSerializer

class CollectionRegistry(plugins: List<BerillPlugin>) {

  val core: List<String> = listOf(Collections.NOTES, Collections.SETTINGS)

  private val owners: Map<String, BerillPlugin>

  init {
    val problems = mutableListOf<String>()
    val taken = core.associateWith { dev.akkraizen.berill.api.i18n.I18n.text("diagnostics.CollectionRegistry.m0") }.toMutableMap()
    val owners = linkedMapOf<String, BerillPlugin>()
    for (plugin in plugins) {
      for (name in plugin.collections.distinct()) {
        if (!NAME.matches(name)) {
          problems +=
            dev.akkraizen.berill.api.i18n.I18n.text("diagnostics.CollectionRegistry.m1", "p0" to (plugin.id), "p1" to (name))
          continue
        }
        val other = taken[name]
        if (other != null) {
          problems += dev.akkraizen.berill.api.i18n.I18n.text("diagnostics.CollectionRegistry.m2", "p0" to (name), "p1" to (other), "p2" to (plugin.id))
          continue
        }
        taken[name] = plugin.id
        owners[name] = plugin
      }
    }
    require(problems.isEmpty()) { dev.akkraizen.berill.api.i18n.I18n.text("diagnostics.CollectionRegistry.m3") + problems.joinToString("\n") }
    this.owners = owners
  }

  val all: List<String> = core + owners.keys

  fun owner(collection: String): BerillPlugin? = owners[collection]

  fun of(plugin: BerillPlugin): List<String> = owners.filterValues { it.id == plugin.id }.keys.toList()

  fun isCore(collection: String): Boolean = collection in core

  fun isKnown(collection: String): Boolean = collection in core || collection in owners

  private companion object {
    val NAME = Regex("[a-z][a-z0-9_]{0,39}")
  }
}

internal class ScopedDataAccess(
  private val inner: DataAccess,
  private val pluginId: String,
  private val allowed: Set<String>
) : DataAccess {
  private fun check(name: String) =
    require(name in allowed) {
      dev.akkraizen.berill.api.i18n.I18n.text("diagnostics.CollectionRegistry.m4", "p0" to (pluginId), "p1" to (name), "p2" to (allowed.sorted()))
    }

  override fun <T> collection(name: String, serializer: KSerializer<T>): DataCollection<T> {
    check(name)
    return inner.collection(name, serializer)
  }

  override fun raw(name: String): RawCollection {
    check(name)
    return inner.raw(name)
  }

  override fun newId(): String = inner.newId()
}
