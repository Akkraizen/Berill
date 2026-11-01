package dev.akkraizen.berill.core

import app.cash.sqldelight.db.SqlDriver
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.DataAccess
import dev.akkraizen.berill.api.FileAccess
import dev.akkraizen.berill.api.HttpAccess
import dev.akkraizen.berill.api.NoFileAccess
import dev.akkraizen.berill.api.NoOAuthRedirects
import dev.akkraizen.berill.api.OAuthRedirects
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.interaction.HotKeys
import dev.akkraizen.berill.core.data.KtorHttpAccess
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.data.StoreDataAccess
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.external.ExternalPluginPlatform
import dev.akkraizen.berill.core.external.ExternalPlugins
import dev.akkraizen.berill.core.external.loadExternalPlugins
import dev.akkraizen.berill.core.nav.AppNavigator
import dev.akkraizen.berill.core.nav.Destination
import dev.akkraizen.berill.core.nav.Tab
import dev.akkraizen.berill.core.notes.NotesRepository
import dev.akkraizen.berill.core.platform.DesktopIntegration
import dev.akkraizen.berill.core.platform.McpAccess
import dev.akkraizen.berill.core.plugin.CollectionRegistry
import dev.akkraizen.berill.core.plugin.PluginHost
import dev.akkraizen.berill.core.secure.MemoryVault
import dev.akkraizen.berill.core.secure.SecretVault
import dev.akkraizen.berill.core.sync.SyncEncryption
import dev.akkraizen.berill.core.sync.SyncEngine
import dev.akkraizen.berill.core.ui.theme.AppearanceCache
import dev.akkraizen.berill.core.ui.theme.AppearanceStore
import dev.akkraizen.berill.core.update.AppUpdates
import dev.akkraizen.berill.core.widgets.WidgetHub
import dev.akkraizen.berill.core.widgets.coreWidgets
import dev.akkraizen.berill.protocol.HybridClock
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BerillCore(
  driver: SqlDriver,
  plugins: List<BerillPlugin>,
  val scope: CoroutineScope,
  val files: FileAccess = NoFileAccess,
  externalPlatform: ExternalPluginPlatform? = null,
  val vault: SecretVault = MemoryVault(),
  val desktop: DesktopIntegration? = null,
  oauth: OAuthRedirects = NoOAuthRedirects,
  private val appearanceCache: AppearanceCache? = null,
  mcpFactory: ((BerillCore) -> McpAccess)? = null,
  reminderScheduler: dev.akkraizen.berill.core.connections.ReminderScheduler = dev.akkraizen.berill.core.connections.NoReminderScheduler,
  attachmentCache: dev.akkraizen.berill.core.attachments.AttachmentCache = dev.akkraizen.berill.core.attachments.MemoryAttachmentCache(),
  updateInstaller: dev.akkraizen.berill.core.update.UpdateInstaller = dev.akkraizen.berill.core.update.NoUpdateInstaller
) {
  private val db = BerillDatabase(driver)

  val deviceId: String = db.recordsQueries.getMeta("device_id").executeAsOneOrNull()
    ?: Uuid.random().toString().take(8).also { db.recordsQueries.setMeta("device_id", it) }

  private val clock = HybridClock(deviceId) { Clock.System.now().toEpochMilliseconds() }

  val store = LocalStore(db, clock, dev.akkraizen.berill.core.data.SearchIndex(driver))
  val data: DataAccess = StoreDataAccess(store)
  val homePreferences = dev.akkraizen.berill.core.preferences.LocalPreferences(store, "home.preferences", dev.akkraizen.berill.core.preferences.HomePreferences.serializer(), dev.akkraizen.berill.core.preferences.HomePreferences())
  val editorPreferences = dev.akkraizen.berill.core.preferences.LocalPreferences(store, "editor.preferences", dev.akkraizen.berill.core.preferences.EditorPreferences.serializer(), dev.akkraizen.berill.core.preferences.EditorPreferences())
  val notificationPreferences = dev.akkraizen.berill.core.preferences.LocalPreferences(store, "notifications.preferences", dev.akkraizen.berill.core.notifications.NotificationPreferences.serializer(), dev.akkraizen.berill.core.notifications.NotificationPreferences())
  val notifications = dev.akkraizen.berill.core.notifications.NotificationCenter(store) { notificationPreferences.state.value }
  val focusedNote = MutableStateFlow<String?>(null)
  val nav = AppNavigator(newNoteId = data::newId)
  val editorSessions = dev.akkraizen.berill.core.notes.EditorSessions()
  val notes = NotesRepository(data) { Clock.System.now().toEpochMilliseconds() }
  val http: HttpAccess = KtorHttpAccess()

  val encryption = SyncEncryption(store, vault)
  val attachments = dev.akkraizen.berill.core.attachments.Attachments(attachmentCache, store, vault, encryption)
  val archive = dev.akkraizen.berill.core.connections.LocalArchiveService(store, attachments)
  private val pluginFiles = object : FileAccess by files {
    override suspend fun storeAttachment(bytes: ByteArray): String = attachments.put(bytes)
    override suspend fun loadAttachment(hash: String): ByteArray? = attachments.get(hash)
  }

  val reminders = dev.akkraizen.berill.core.connections.ReminderCenter(store, reminderScheduler, notificationPreferences) { error ->
    connections.errors.report(dev.akkraizen.berill.core.connections.FailureOperation.REMINDERS, error)
  }.also { center -> center.onDaily = { notifications.reminders("daily", it) }; center.onTimer = { notifications.reminders("timer", listOf(it)) }; center.onCancel = notifications::cancelReminder }

  val external: ExternalPlugins = loadExternalPlugins(externalPlatform, plugins, scope)
  val collections = CollectionRegistry(plugins + external.loaded)
  val plugins = PluginHost(plugins + external.loaded, store, data, http, nav, scope, pluginFiles, vault, oauth, reminders)
  val appearance = AppearanceStore(store, scope)

  val widgets: WidgetHub = WidgetHub(
    plugins = this.plugins,
    nav = nav,
    store = store,
    coreProviders = coreWidgets(notes) { except -> widgets.summaries(except) },
    now = { Clock.System.now().toEpochMilliseconds() }
  )

  val sync = SyncEngine(store, deviceId, scope, encryption, vault, beforeUpload = { attachments.uploadPending() })

  var healthImport: dev.akkraizen.berill.core.connections.HealthImport? = null

  val shortcuts = dev.akkraizen.berill.core.connections.ShortcutSettings(store)
  val connections = dev.akkraizen.berill.core.connections.Connections(this)

  val updates = AppUpdates(store, updateInstaller, scope) { editorSessions.prepareMutation() }

  val mcp: McpAccess? = mcpFactory?.invoke(this)

  private val _onboarded = MutableStateFlow(store.meta(ONBOARDING_KEY) == "1")

  val onboarded: StateFlow<Boolean> = _onboarded

  fun finishOnboarding() {
    store.setMeta(ONBOARDING_KEY, "1")
    _onboarded.value = true
  }

  fun onEscapeKey(): Boolean {
    for (dialog in listOf(notifications.conflictsOpen, notifications.open, connections.searchOpen, connections.dayOpen, connections.trashOpen)) {
      if (dialog.value) { dialog.value = false; return true }
    }
    if (HotKeys.runEscape()) return true
    if (focusedNote.value != null) { focusedNote.value = null; return true }
    if (!_onboarded.value) return false
    if (nav.stack.value == listOf(Destination.Home)) return false
    nav.selectTab(Tab.Home)
    return true
  }

  fun onEnterKey(): Boolean = HotKeys.runPrimary()

  fun replayOnboarding() {
    store.setMeta(ONBOARDING_KEY, "0")
    _onboarded.value = false
  }

  private companion object {
    const val ONBOARDING_KEY = "onboarding_done"
  }

  init {
    scope.launch { store.conflicts.state.collect { notifications.conflicts(it) } }
    scope.launch { updates.state.collect { state ->
      if (state is dev.akkraizen.berill.core.update.UpdateCheck.Available) notifications.publish(dev.akkraizen.berill.core.notifications.Notice("update:${state.update.version}", dev.akkraizen.berill.core.notifications.NoticeKind.UPDATE, at = Clock.System.now().toEpochMilliseconds(), detail = state.update.version))
    } }
    I18n.language = appearance.state.value.appLanguage
    scope.launch {
      appearance.state.collect {
        I18n.language = it.appLanguage
        appearanceCache?.let { cache -> runCatching { cache.write(it) } }
      }
    }
  }

  fun start() {
    scope.launch {
      while (true) {
        for (plugin in this@BerillCore.plugins.enabled.value) {
          for (source in dev.akkraizen.berill.core.connections.tolerateSource(plugin.id, {}) { plugin.provides(this@BerillCore.plugins.context(plugin)) }.orEmpty().filterIsInstance<dev.akkraizen.berill.api.MaintenanceSource>()) {
            try { source.update() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { dev.akkraizen.berill.core.log.AppLog.error("maintenance", plugin.id, e) }
          }
        }
        kotlinx.coroutines.delay(60000)
      }
    }
    reminders.start(this.plugins, scope)
    sync.start()
    updates.start(scope)
    desktop?.start()
    mcp?.start()
  }
}
