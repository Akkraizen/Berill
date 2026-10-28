package dev.akkraizen.berill.core.widgets

import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetChoice
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.nav.AppNavigator
import dev.akkraizen.berill.core.nav.Tab
import dev.akkraizen.berill.core.plugin.PluginHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class WidgetEntry(val key: String, val ownerId: String, val ownerTitle: () -> String, val provider: WidgetProvider) {
  val title: String
    get() = provider.title()

  val description: String
    get() = provider.description()
}

@Serializable
data class WidgetInstance(val key: String, val choice: String? = null, val state: Map<String, String> = emptyMap())

@Serializable
data class WidgetSnapshot(val generatedAtMillis: Long, val widgets: Map<String, WidgetContent>)

class WidgetHub(
  private val plugins: PluginHost,
  private val nav: AppNavigator,
  private val store: LocalStore,
  coreProviders: List<WidgetProvider>,
  private val now: () -> Long
) {
  private val coreEntries: List<WidgetEntry> = coreProviders.map { WidgetEntry(key(CORE, it.id), CORE, { WidgetText.appName() }, it) }

  private val pluginEntries: List<WidgetEntry> by lazy {
    plugins.all.flatMap { plugin ->
      val provided = runCatching { plugin.widgets(plugins.context(plugin)) }
        .onFailure { AppLog.error("widgets", "${plugin.id} widgets failed", it) }
        .getOrDefault(emptyList())
      provided.map { WidgetEntry(key(plugin.id, it.id), plugin.id, { plugin.title }, it) }
    }
  }

  init {
    val keys = coreEntries.map { it.key }
    require(keys.size == keys.toSet().size) { "Duplicate core widget ids: $keys" }
  }

  val all: List<WidgetEntry>
    get() = coreEntries + pluginEntries

  fun available(): List<WidgetEntry> = all.filter(::isEnabled)

  fun entry(key: String): WidgetEntry? = all.firstOrNull { it.key == key }

  suspend fun choices(key: String): List<WidgetChoice> = entry(key)?.provider?.choices?.let { guarded("$key choices") { it.list() } }.orEmpty()

  private suspend fun <T> guarded(what: String, block: suspend () -> T): T? =
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      AppLog.error("widgets", "$what failed", e)
      null
    }

  fun needsChoice(key: String): Boolean = entry(key)?.provider?.choices?.required == true

  private fun isEnabled(entry: WidgetEntry): Boolean = entry.ownerId == CORE || plugins.enabled.value.any { it.id == entry.ownerId }

  private fun request(instance: WidgetInstance) = WidgetRequest(instance.choice, instance.state, now())

  suspend fun content(instance: WidgetInstance): WidgetContent {
    val entry = entry(instance.key) ?: return notice(WidgetText.unavailable(), WidgetText.unavailableHint())
    if (!isEnabled(entry)) return notice(entry.ownerTitle(), WidgetText.moduleDisabled())
    if (entry.provider.choices?.required == true && instance.choice == null)
      return notice(entry.title, WidgetText.chooseHint())
    return guarded("${instance.key} content") { entry.provider.content(request(instance)) } ?: notice(entry.title, WidgetText.failed())
  }

  suspend fun perform(instance: WidgetInstance, action: WidgetAction): WidgetEffect {
    val entry = entry(instance.key)
    return when (action) {
      is WidgetAction.Open -> {
        val target = action.pluginId ?: entry?.ownerId?.takeIf { it != CORE }
        if (target == null) nav.selectTab(Tab.Home) else nav.showPlugin(target, action.route, action.page)
        WidgetEffect.OpenApp()
      }
      is WidgetAction.OpenNote -> {
        nav.showNote(action.noteId)
        WidgetEffect.OpenApp()
      }
      WidgetAction.OpenHome -> {
        nav.selectTab(Tab.Home)
        WidgetEffect.OpenApp()
      }
      is WidgetAction.Run -> {
        if (entry == null || !isEnabled(entry)) return WidgetEffect.Refresh()
        guarded("${instance.key} action ${action.id}") { entry.provider.perform(request(instance), action) } ?: WidgetEffect.Refresh()
      }
    }
  }

  suspend fun summaries(except: String? = null): List<WidgetRow> =
    available()
      .filter { it.key != except && it.provider.choices?.required != true }
      .mapNotNull { entry ->
        guarded("${entry.key} summary") { entry.provider.summary(WidgetRequest(nowMillis = now())) }
      }

  @OptIn(FlowPreview::class)
  val changes: Flow<Unit> = merge(store.changes, plugins.enabled.drop(1).map { }).debounce(CHANGE_DEBOUNCE_MS)

  suspend fun snapshot(instances: List<WidgetInstance> = available().filterNot { it.provider.choices?.required == true }.map { WidgetInstance(it.key) }): WidgetSnapshot =
    WidgetSnapshot(now(), instances.associate { instanceId(it) to content(it) })

  fun encodeSnapshot(snapshot: WidgetSnapshot): String = json.encodeToString(WidgetSnapshot.serializer(), snapshot)

  fun encode(content: WidgetContent): String = json.encodeToString(WidgetContent.serializer(), content)

  fun decode(text: String): WidgetContent? = decodeContent(text)

  fun encodeAction(action: WidgetAction): String = json.encodeToString(WidgetAction.serializer(), action)

  fun decodeAction(text: String): WidgetAction? = runCatching { json.decodeFromString(WidgetAction.serializer(), text) }.getOrNull()

  companion object {
    const val CORE = "core"
    private const val CHANGE_DEBOUNCE_MS = 700L

    private val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = false
    }

    fun key(ownerId: String, widgetId: String) = "$ownerId.$widgetId"

    fun decodeContent(text: String): WidgetContent? = runCatching { json.decodeFromString(WidgetContent.serializer(), text) }.getOrNull()

    fun instanceId(instance: WidgetInstance) = if (instance.choice == null) instance.key else "${instance.key}:${instance.choice}"

    fun notice(title: String, text: String) = WidgetContent(title = title, blocks = listOf(WidgetBlock.Text(text, WidgetTone.Muted)))
  }
}
