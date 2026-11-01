package dev.akkraizen.berill.core.update

import dev.akkraizen.berill.core.BuildInfo
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.sync.clientPlatform
import dev.akkraizen.berill.core.sync.createHttpClient
import dev.akkraizen.berill.protocol.LatestRelease
import dev.akkraizen.berill.protocol.compareVersions
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@kotlinx.serialization.Serializable
data class AppUpdate(
  val version: String,
  val url: String?,
  val sha256: String? = null,
  val size: Long? = null,
  val notes: String? = null,
  val signature: String? = null,
)

@kotlinx.serialization.Serializable
private data class SavedPackage(val update: AppUpdate, val path: String)

sealed interface UpdateCheck {
  data object Unknown : UpdateCheck

  data object Checking : UpdateCheck

  data object UpToDate : UpdateCheck

  data class Available(val update: AppUpdate) : UpdateCheck

  data class Failed(val message: String) : UpdateCheck
}

class AppUpdates(
  private val store: LocalStore,
  private val installer: UpdateInstaller = NoUpdateInstaller,
  private val scope: CoroutineScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
  private val beforeInstall: suspend () -> Unit = {},
) {
  val installationAvailable get() = installer.available
  private val _transfer = MutableStateFlow<UpdateTransfer>(installer.previousFailure?.let { UpdateTransfer.Failed(it) } ?: UpdateTransfer.Idle)
  val transfer: StateFlow<UpdateTransfer> = _transfer
  val exitRequested = MutableStateFlow(false)
  private val stateLock = SynchronizedObject()
  private var generation = 0
  private var operation: kotlinx.coroutines.Job? = null
  private val checkMutex = kotlinx.coroutines.sync.Mutex()

  fun download(update: AppUpdate) {
    if (operation?.isActive == true || _transfer.value is UpdateTransfer.Ready) return
    val request = synchronized(stateLock) { ++generation }
    operation = scope.launch {
      synchronized(stateLock) { if (request == generation) _transfer.value = UpdateTransfer.Downloading(update, 0, update.size) }
      try {
        if (!installer.available) throw UpdateException(UpdateFailure.UNSUPPORTED)
        if (update.sha256 == null || update.size == null) throw UpdateException(UpdateFailure.SERVER_TOO_OLD)
        val path = installer.download(update) { received, total -> synchronized(stateLock) { if (request == generation) _transfer.value = UpdateTransfer.Downloading(update, received, total) } }
        try { installer.validate(path, update) }
        catch (e: Exception) { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { installer.remove(path) }; throw e }
        val retained = synchronized(stateLock) {
          if (request != generation) false else {
            store.setMeta(KEY_PACKAGE, dev.akkraizen.berill.core.sync.syncJson.encodeToString(SavedPackage(update, path)))
            _transfer.value = UpdateTransfer.Ready(update, path)
            true
          }
        }
        if (!retained) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { installer.remove(path) }
      } catch (e: kotlinx.coroutines.CancellationException) { synchronized(stateLock) { if (request == generation) _transfer.value = UpdateTransfer.Idle }; throw e }
      catch (e: Exception) {
        AppLog.warn(TAG, "update download failed", e)
        synchronized(stateLock) { if (request == generation) _transfer.value = UpdateTransfer.Failed((e as? UpdateException)?.reason ?: UpdateFailure.DOWNLOAD) }
      }
    }
  }

  fun cancel() {
    if (_transfer.value is UpdateTransfer.Installing) return
    val ready = synchronized(stateLock) {
      generation++
      operation?.cancel()
      val retained = _transfer.value as? UpdateTransfer.Ready ?: (_transfer.value as? UpdateTransfer.Failed)?.ready
      store.setMeta(KEY_PACKAGE, "")
      _transfer.value = UpdateTransfer.Idle
      retained
    }
    if (ready != null) scope.launch { installer.remove(ready.path) }
  }

  fun install() {
    if (operation?.isActive == true) return
    val ready = _transfer.value as? UpdateTransfer.Ready ?: (_transfer.value as? UpdateTransfer.Failed)?.ready ?: return
    operation = scope.launch {
      _transfer.value = UpdateTransfer.Installing(ready.update)
      try {
        installer.validate(ready.path, ready.update)
        beforeInstall()
        val result = installer.install(ready.path, ready.update)
        if (result == InstallResult.EXIT_REQUIRED) exitRequested.value = true
        else _transfer.value = ready.copy(hint = result)
      } catch (e: kotlinx.coroutines.CancellationException) { _transfer.value = ready; throw e }
      catch (e: Exception) {
        AppLog.warn(TAG, "update installation failed", e)
        _transfer.value = UpdateTransfer.Failed((e as? UpdateException)?.reason ?: UpdateFailure.INSTALL, ready)
      }
    }
  }

  private val http = createHttpClient()
  private val _state = MutableStateFlow<UpdateCheck>(UpdateCheck.Unknown)
  val state: StateFlow<UpdateCheck> = _state

  init {
    val saved = store.meta(KEY_PACKAGE)?.takeIf { it.isNotBlank() }?.let {
      runCatching { dev.akkraizen.berill.core.sync.syncJson.decodeFromString<SavedPackage>(it) }.getOrNull()
    }
    if (saved != null && installer.available && compareVersions(saved.update.version, BuildInfo.VERSION_NAME) > 0 && saved.update.url?.startsWith(serverUrl() + "/download/") == true) {
      val ready = UpdateTransfer.Ready(saved.update, saved.path)
      val failed = _transfer.value as? UpdateTransfer.Failed
      _transfer.value = if (failed != null) failed.copy(ready = ready) else ready
      _state.value = UpdateCheck.Available(saved.update)
    } else if (saved != null && installer.available) {
      store.setMeta(KEY_PACKAGE, "")
      scope.launch { runCatching { installer.remove(saved.path) } }
    }
  }

  fun serverUrl(): String = (store.meta(KEY_URL) ?: BuildInfo.UPDATES_URL).trim().trimEnd('/')

  fun setServerUrl(url: String) {
    if (_transfer.value is UpdateTransfer.Installing) return
    cancel()
    store.setMeta(KEY_URL, url.trim().trimEnd('/'))
    _state.value = UpdateCheck.Unknown
  }

  fun start(scope: CoroutineScope) {
    scope.launch {
      delay(FIRST_CHECK_DELAY)
      while (true) {
        check()
        delay(CHECK_INTERVAL)
      }
    }
  }

  suspend fun check() = checkMutex.withLock {
    if (_transfer.value !is UpdateTransfer.Idle && _transfer.value !is UpdateTransfer.Failed) return@withLock
    val url = serverUrl().takeIf { it.isNotEmpty() } ?: return@withLock
    _state.value = UpdateCheck.Checking
    val checked = try {
      val response = http.get("$url/api/release")
      when {
        response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.NotFound -> UpdateCheck.UpToDate
        !response.status.isSuccess() -> UpdateCheck.Failed(response.status.toString())
        else -> {
          val latest: LatestRelease = response.body()
          if (compareVersions(latest.version, BuildInfo.VERSION_NAME) > 0) {
            val path = when (clientPlatform) {
              "android" -> latest.android
              "macos" -> latest.macos
              "linux" -> latest.linuxDeb ?: latest.linuxRpm
              else -> latest.windows
            }
            AppLog.info(TAG, "update available: ${latest.version}")
            val platform = installer.platform.ifBlank { clientPlatform }
            val artifact = latest.artifacts[platform] ?: if (platform == "linux") latest.artifacts["linux-deb"] else null
            val selectedPath = artifact?.path ?: path
            require(selectedPath == null || selectedPath.startsWith("/download/") && !selectedPath.contains("..") && !selectedPath.contains('?') && !selectedPath.contains('#'))
            UpdateCheck.Available(AppUpdate(latest.version, selectedPath?.let { "$url$it" }, artifact?.sha256, artifact?.size, latest.notes, artifact?.signature))
          } else {
            UpdateCheck.UpToDate
          }
        }
      }
    } catch (e: kotlinx.coroutines.CancellationException) { if (url == serverUrl()) _state.value = UpdateCheck.Unknown; throw e }
      catch (e: Exception) { UpdateCheck.Failed(e.message ?: e::class.simpleName.orEmpty()) }
    if (url == serverUrl()) _state.value = checked
  }

  private companion object {
    const val TAG = "update"
    const val KEY_PACKAGE = "updates_downloaded_package"
    const val KEY_URL = "updates_url"
    const val FIRST_CHECK_DELAY = 15_000L
    const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L
  }
}
