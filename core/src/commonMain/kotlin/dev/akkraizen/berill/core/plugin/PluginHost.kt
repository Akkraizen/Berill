package dev.akkraizen.berill.core.plugin

import dev.akkraizen.berill.api.Capability
import dev.akkraizen.berill.api.CapabilityLookup
import dev.akkraizen.berill.api.DataAccess
import dev.akkraizen.berill.api.FileAccess
import dev.akkraizen.berill.api.HttpAccess
import dev.akkraizen.berill.api.Navigator
import dev.akkraizen.berill.api.NoFileAccess
import dev.akkraizen.berill.api.NoOAuthRedirects
import dev.akkraizen.berill.api.OAuthRedirects
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginSettings
import dev.akkraizen.berill.api.SecretStore
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.nav.AppNavigator
import dev.akkraizen.berill.core.nav.Destination
import dev.akkraizen.berill.core.secure.MemoryVault
import dev.akkraizen.berill.core.secure.PluginSecrets
import dev.akkraizen.berill.core.secure.SecretVault
import dev.akkraizen.berill.protocol.Collections
import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.reflect.KClass

class PluginHost(
  plugins: List<BerillPlugin>,
  private val store: LocalStore,
  private val data: DataAccess,
  private val http: HttpAccess,
  private val nav: AppNavigator,
  private val scope: CoroutineScope,
  private val files: FileAccess = NoFileAccess,
  private val vault: SecretVault = MemoryVault(),
  private val redirects: OAuthRedirects = NoOAuthRedirects
) {

  val all: List<BerillPlugin> = plugins.sortedWith(compareBy({ it.order }, { it.title }))

  init {
    val duplicates = all.groupBy { it.id }.filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) { "Повторяются id плагинов: $duplicates" }
    all.forEach { p ->
      require(ID.matches(p.id)) { "Некорректный id плагина `${p.id}`: только a-z, 0-9 и _" }
      require(p.id !in RESERVED) { "id `${p.id}` зарезервирован ядром" }
      val pageIds = p.pages.map { it.id }
      require(pageIds.all { PAGE_ID.matches(it) }) {
        "`${p.id}`: некорректный id страницы в $pageIds"
      }
      require(pageIds.size == pageIds.toSet().size) { "`${p.id}`: повторяются id страниц $pageIds" }
    }
  }

  private class Flags(val installed: Map<String, Boolean>, val enabled: Map<String, Boolean>)

  private fun readFlags(records: List<SyncRecord>) =
    Flags(
      installed = parseSwitches(records.firstOrNull { it.id == INSTALLED_RECORD }),
      enabled = parseSwitches(records.firstOrNull { it.id == PLUGINS_RECORD })
    )

  private val flags: StateFlow<Flags> = store
    .observe(Collections.SETTINGS)
    .map(::readFlags)
    .stateIn(
      scope,
      SharingStarted.Eagerly,
      readFlags(
        listOfNotNull(
          store.get(Collections.SETTINGS, INSTALLED_RECORD)?.takeUnless { it.deleted },
          store.get(Collections.SETTINGS, PLUGINS_RECORD)?.takeUnless { it.deleted }
        )
      )
    )

  private fun Flags.isInstalled(p: BerillPlugin) = installed[p.id] ?: p.installedByDefault

  private fun Flags.isEnabled(p: BerillPlugin) = isInstalled(p) && (enabled[p.id] ?: true)

  val installed: StateFlow<List<BerillPlugin>> = flags
    .map { f -> all.filter { f.isInstalled(it) } }
    .stateIn(scope, SharingStarted.Eagerly, all.filter { flags.value.isInstalled(it) })

  val available: StateFlow<List<BerillPlugin>> = flags
    .map { f -> all.filterNot { f.isInstalled(it) } }
    .stateIn(scope, SharingStarted.Eagerly, all.filterNot { flags.value.isInstalled(it) })

  val enabled: StateFlow<List<BerillPlugin>> = flags
    .map { f -> all.filter { f.isEnabled(it) } }
    .stateIn(scope, SharingStarted.Eagerly, all.filter { flags.value.isEnabled(it) })

  fun setEnabled(
    pluginId: String,
    on: Boolean
  ) = store.put(Collections.SETTINGS, PLUGINS_RECORD, mapOf(pluginId to JsonPrimitive(on)))

  fun install(pluginId: String) {
    store.put(Collections.SETTINGS, INSTALLED_RECORD, mapOf(pluginId to JsonPrimitive(true)))
    setEnabled(pluginId, true)
  }

  fun uninstall(pluginId: String) = store.put(Collections.SETTINGS, INSTALLED_RECORD, mapOf(pluginId to JsonPrimitive(false)))

  fun byId(id: String): BerillPlugin? = all.firstOrNull { it.id == id }

  private val lookup = object : CapabilityLookup {
    override fun <T : Capability> all(type: KClass<T>): List<T> =
      enabled.value
        .flatMap { provided[it.id].orEmpty() }
        .filter { type.isInstance(it) }
        .map {
          @Suppress("UNCHECKED_CAST")
          it as T
        }
  }

  private val contexts: Map<String, PluginContext> = all.associate { it.id to Context(it) }

  fun context(plugin: BerillPlugin): PluginContext = contexts.getValue(plugin.id)

  private val provided: Map<String, List<Capability>> by lazy {
    all.associate { p -> p.id to runCatching { p.provides(context(p)) }.getOrDefault(emptyList()) }
  }

  private inner class Context(plugin: BerillPlugin) : PluginContext {
    override val pluginId: String = plugin.id

    override val data: DataAccess = ScopedDataAccess(this@PluginHost.data, plugin.id, plugin.collections.toSet())
    override val nav: Navigator = PluginNavigator(plugin, this@PluginHost.nav, scope)
    override val capabilities: CapabilityLookup
      get() = lookup

    override val settings: PluginSettings = StorePluginSettings(store, pluginId)

    override val http: HttpAccess = GuardedHttpAccess(this@PluginHost.http, plugin.permissions)
    override val files: FileAccess
      get() = this@PluginHost.files

    override val scope: CoroutineScope
      get() = this@PluginHost.scope

    override val secrets: SecretStore = PluginSecrets(this@PluginHost.vault, plugin.id)

    override val oauth: OAuthRedirects
      get() = this@PluginHost.redirects
  }

  private fun parseSwitches(record: SyncRecord?): Map<String, Boolean> =
    record
      ?.fields
      .orEmpty()
      .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.booleanOrNull?.let { k to it } }
      .toMap()

  companion object {
    const val PLUGINS_RECORD = "plugins"
    const val INSTALLED_RECORD = "plugins_installed"
    private val ID = Regex("[a-z][a-z0-9_]{1,31}")
    private val PAGE_ID = Regex("[a-z0-9][a-z0-9_-]{0,31}")

    val RESERVED = setOf("core", "notes", "home", "settings", "sections", "sync", "plugins")
  }
}

private class PluginNavigator(
  private val plugin: BerillPlugin,
  private val app: AppNavigator,
  scope: CoroutineScope
) : Navigator {
  private val pluginId = plugin.id

  private fun last(stack: List<Destination>) = stack.lastOrNull { it is Destination.Plugin && it.id == pluginId } as Destination.Plugin?

  override val route: StateFlow<String?> =
    app.stack.map { stack -> last(stack)?.route }.stateIn(scope, SharingStarted.Eagerly, null)

  override val page: StateFlow<String?> = app.stack
    .map { stack -> resolvePage(plugin, last(stack)?.page) }
    .stateIn(scope, SharingStarted.Eagerly, resolvePage(plugin, null))

  override fun open(route: String?) = app.push(Destination.Plugin(pluginId, route, page.value))

  override fun openPage(pageId: String) = app.openSection(pluginId, pageId)

  override fun back(): Boolean = app.back()

  override fun openPlugin(pluginId: String, route: String?, page: String?) = app.openPlugin(pluginId, route, page)

  override fun openNote(noteId: String?) = app.openNote(noteId)

  override fun openSettings() = app.openPluginSettings(pluginId)
}

internal fun resolvePage(plugin: BerillPlugin, requested: String?): String? {
  val pages = plugin.pages
  if (pages.isEmpty()) return null
  return pages.firstOrNull { it.id == requested }?.id ?: pages.first().id
}

private class StorePluginSettings(private val store: LocalStore, pluginId: String) : PluginSettings {
  private val recordId = "plugin.$pluginId"

  override fun get(key: String): JsonElement? = store.get(Collections.SETTINGS, recordId)?.takeUnless { it.deleted }?.fields?.get(key)

  override fun observe(key: String): Flow<JsonElement?> =
    store
      .observe(Collections.SETTINGS)
      .map { records -> records.firstOrNull { it.id == recordId }?.fields?.get(key) }
      .distinctUntilChanged()

  override fun set(key: String, value: JsonElement) = store.put(Collections.SETTINGS, recordId, mapOf(key to value))
}
