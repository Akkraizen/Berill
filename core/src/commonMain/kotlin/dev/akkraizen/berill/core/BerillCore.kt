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
import dev.akkraizen.berill.core.plugin.CollectionRegistry
import dev.akkraizen.berill.core.plugin.PluginHost
import dev.akkraizen.berill.core.secure.MemoryVault
import dev.akkraizen.berill.core.secure.SecretVault
import dev.akkraizen.berill.core.sync.SyncEncryption
import dev.akkraizen.berill.core.sync.SyncEngine
import dev.akkraizen.berill.core.ui.theme.AppearanceStore
import dev.akkraizen.berill.core.update.AppUpdates
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
  oauth: OAuthRedirects = NoOAuthRedirects
) {
  private val db = BerillDatabase(driver)

  val deviceId: String = db.recordsQueries.getMeta("device_id").executeAsOneOrNull()
    ?: Uuid.random().toString().take(8).also { db.recordsQueries.setMeta("device_id", it) }

  private val clock = HybridClock(deviceId) { Clock.System.now().toEpochMilliseconds() }

  val store = LocalStore(db, clock)
  val data: DataAccess = StoreDataAccess(store)
  val nav = AppNavigator(newNoteId = data::newId)
  val notes = NotesRepository(data) { Clock.System.now().toEpochMilliseconds() }
  val http: HttpAccess = KtorHttpAccess()

  val external: ExternalPlugins = loadExternalPlugins(externalPlatform, plugins, scope)
  val collections = CollectionRegistry(plugins + external.loaded)
  val plugins = PluginHost(plugins + external.loaded, store, data, http, nav, scope, files, vault, oauth)
  val appearance = AppearanceStore(store, scope)

  val encryption = SyncEncryption(store, vault)
  val sync = SyncEngine(store, deviceId, scope, encryption, vault)

  val updates = AppUpdates(store)

  private val _onboarded = MutableStateFlow(store.meta(ONBOARDING_KEY) == "1")

  val onboarded: StateFlow<Boolean> = _onboarded

  fun finishOnboarding() {
    store.setMeta(ONBOARDING_KEY, "1")
    _onboarded.value = true
  }

  fun onEscapeKey(): Boolean {
    if (HotKeys.runEscape()) return true
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
    I18n.language = appearance.state.value.appLanguage
    scope.launch { appearance.state.collect { I18n.language = it.appLanguage } }
  }

  fun start() {
    sync.start()
    updates.start(scope)
    desktop?.start()
  }
}
