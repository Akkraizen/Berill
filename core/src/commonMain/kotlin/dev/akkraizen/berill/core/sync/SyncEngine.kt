package dev.akkraizen.berill.core.sync

import dev.akkraizen.berill.core.BuildInfo
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.secure.MemoryVault
import dev.akkraizen.berill.core.secure.SecretVault
import dev.akkraizen.berill.core.ui.CoreText
import dev.akkraizen.berill.protocol.ClientInfo
import dev.akkraizen.berill.protocol.Collections
import dev.akkraizen.berill.protocol.SyncRecord
import dev.akkraizen.berill.protocol.SyncRequest
import dev.akkraizen.berill.protocol.SyncResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncState {
  data object NotConfigured : SyncState

  data object Idle : SyncState

  data object Syncing : SyncState

  data class Done(val atMillis: Long, val received: Int, val sent: Int) : SyncState

  data class Failed(val message: String) : SyncState
}

class SyncEngine(
  private val store: LocalStore,
  private val deviceId: String,
  private val scope: CoroutineScope,
  val encryption: SyncEncryption,
  private val vault: SecretVault = MemoryVault()
) {
  private val http = createHttpClient()
  private val mutex = Mutex()
  private val client = ClientInfo(clientDeviceName(), clientPlatform, BuildInfo.VERSION_NAME)

  private var loop: Job? = null

  private val _state =
    MutableStateFlow<SyncState>(if (serverUrl() == null) SyncState.NotConfigured else SyncState.Idle)
  val state: StateFlow<SyncState> = _state

  private val _options = MutableStateFlow(SyncOptions.decode(store.meta(KEY_OPTIONS)))
  val options: StateFlow<SyncOptions> = _options

  fun setOptions(new: SyncOptions) {
    val old = _options.value
    if (new == old) return
    store.setMeta(KEY_OPTIONS, new.encode())
    _options.value = new
    if (new.widens(old)) {
      scope.launch {
        mutex.withLock { store.setMeta(KEY_CURSOR, "0") }
        syncNow()
      }
    }
  }

  fun serverUrl(): String? = store.meta(KEY_URL)?.takeIf { it.isNotBlank() }

  fun token(): String? {
    store
      .meta(KEY_TOKEN)
      ?.takeIf { it.isNotBlank() }
      ?.let { old ->
        vault.write(VAULT_TOKEN, old)
        store.setMeta(KEY_TOKEN, "")
      }
    return vault.read(VAULT_TOKEN)?.takeIf { it.isNotBlank() }
  }

  fun configure(url: String, token: String) {
    scope.launch {
      mutex.withLock {
        AppLog.info(TAG, "server configured: ${url.trim().trimEnd('/')}")
        store.setMeta(KEY_URL, url.trim().trimEnd('/'))
        vault.write(VAULT_TOKEN, token.trim())
        store.setMeta(KEY_CURSOR, "0")
        store.setMeta(KEY_RECONCILED, "")
        store.setMeta(KEY_DATASET, "")
        _state.value = SyncState.Idle
        syncLocked()
      }
    }
  }

  @OptIn(FlowPreview::class)
  fun start() {
    if (loop != null) return
    loop = scope.launch {
      options
        .map { it.auto to it.intervalMinutes }
        .distinctUntilChanged()
        .collectLatest { (auto, minutes) ->
          if (!auto) return@collectLatest
          coroutineScope {
            launch { store.localChanges.debounce(2_000).collect { syncNow() } }
            while (true) {
              syncNow()
              delay(minutes * 60_000L)
            }
          }
        }
    }
  }

  suspend fun syncNow(reconcile: Boolean = false) {
    mutex.withLock {
      if (reconcile) store.setMeta(KEY_RECONCILED, "")
      syncLocked()
    }
  }

  private suspend fun syncLocked() {
    val url = serverUrl()
    val token = token()
    if (url == null || token == null) {
      _state.value = SyncState.NotConfigured
      return
    }
    _state.value = SyncState.Syncing
    try {
      var result = runPass(url, token)
      if (result == null) {
        store.setMeta(KEY_RECONCILED, "")
        result = runPass(url, token) ?: error(CoreText.errorFallback())
      }
      val (received, sent, skipped) = result
      store.setMeta(KEY_LAST_OK, Clock.System.now().toEpochMilliseconds().toString())
      if (received > 0 || sent > 0) AppLog.info(TAG, "done: received $received, sent $sent, cursor ${store.meta(KEY_CURSOR)}")
      if (skipped > 0) AppLog.warn(TAG, "$skipped encrypted records could not be decrypted")
      _state.value = when {
        encryption.state.value == E2eState.Locked -> SyncState.Failed(CoreText.e2eLockedSync())
        skipped > 0 -> SyncState.Failed(CoreText.e2eSkipped(skipped)())
        else -> SyncState.Done(Clock.System.now().toEpochMilliseconds(), received, sent)
      }
    } catch (e: Exception) {
      val message = e.message ?: e::class.simpleName ?: CoreText.errorFallback()
      AppLog.warn(TAG, "failed: $message", e)
      store.setMeta(KEY_LAST_ERROR, "${Clock.System.now().toEpochMilliseconds()}|$message")
      _state.value = SyncState.Failed(message)
    }
  }

  private class DatasetChanged : Exception()

  private suspend fun runPass(url: String, token: String): Triple<Int, Int, Int>? {
    if (store.meta(KEY_RECONCILED) != "1") {
      AppLog.info(TAG, "full resend: all ${store.countAll()} local records are queued")
      store.enqueueAllForSync()
      store.setMeta(KEY_CURSOR, "0")
    }
    var cursor = store.meta(KEY_CURSOR)?.toLongOrNull() ?: 0L
    val options = _options.value
    var received = 0
    var skipped = 0

    suspend fun exchange(records: List<SyncRecord>): SyncResponse {
      val response = http.post("$url/api/sync") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(SyncRequest(deviceId, cursor, records, client))
      }
      if (!response.status.isSuccess()) error(CoreText.serverResponded(response.status.toString())())
      val body: SyncResponse = response.body()
      body.user?.let { if (store.meta(KEY_USER) != it) store.setMeta(KEY_USER, it) }
      body.dataset?.let { dataset ->
        val known = store.meta(KEY_DATASET).orEmpty()
        if (known != dataset) {
          store.setMeta(KEY_DATASET, dataset)
          if (known.isNotEmpty()) {
            AppLog.warn(TAG, "server dataset changed ($known -> $dataset): the server database was recreated, resending everything")
            throw DatasetChanged()
          }
        }
      }
      val incoming = body.records.filter { options.includes(it.collection, it.id) || isE2eParams(it) }
      store.applyRemote(incoming.filter(::isE2eParams))
      encryption.refresh()
      val decrypted = incoming.filterNot(::isE2eParams).mapNotNull { encryption.decryptIncoming(it) }
      skipped += incoming.size - decrypted.size - incoming.count(::isE2eParams)
      store.applyRemote(decrypted)
      cursor = body.cursor
      store.setMeta(KEY_CURSOR, cursor.toString())
      received += body.records.size
      return body
    }

    try {
      do {
        val body = exchange(emptyList())
      } while (body.hasMore)

      val dirty = store.dirty().filter { (record, _) -> options.includes(record.collection, record.id) || isE2eParams(record) }
      val outgoing = dirty.mapNotNull { (record, version) -> encryption.encryptOutgoing(record)?.let { Triple(record, version, it) } }
      for (batch in batches(outgoing)) {
        var body = exchange(batch.map { it.third })
        batch.forEach { (record, version, _) -> store.markClean(record, version) }
        while (body.hasMore) body = exchange(emptyList())
      }
      if (skipped == 0 && encryption.state.value != E2eState.Locked) store.setMeta(KEY_RECONCILED, "1")
      return Triple(received, outgoing.size, skipped)
    } catch (e: DatasetChanged) {
      return null
    }
  }

  private fun batches(items: List<Outgoing>): List<List<Outgoing>> {
    val result = mutableListOf<List<Outgoing>>()
    var current = mutableListOf<Outgoing>()
    var bytes = 0
    for (item in items) {
      val size = item.third.fields.toString().length + 200
      if (current.isNotEmpty() && (current.size >= BATCH_RECORDS || bytes + size > BATCH_BYTES)) {
        result += current
        current = mutableListOf()
        bytes = 0
      }
      current += item
      bytes += size
    }
    if (current.isNotEmpty()) result += current
    return result
  }

  fun diagnostics(): SyncDiagnostics {
    val options = _options.value
    val dirty = store.dirty()
    return SyncDiagnostics(
      serverUrl = serverUrl(),
      hasToken = token() != null,
      user = store.meta(KEY_USER)?.takeIf { it.isNotBlank() },
      dataset = store.meta(KEY_DATASET)?.takeIf { it.isNotBlank() },
      cursor = store.meta(KEY_CURSOR)?.toLongOrNull() ?: 0L,
      fullResendPending = store.meta(KEY_RECONCILED) != "1",
      pending = dirty.count { (r, _) -> options.includes(r.collection, r.id) || isE2eParams(r) },
      pendingExcluded = dirty.count { (r, _) -> !(options.includes(r.collection, r.id) || isE2eParams(r)) },
      totalRecords = store.countAll(),
      lastOkMillis = store.meta(KEY_LAST_OK)?.toLongOrNull(),
      lastError = store.meta(KEY_LAST_ERROR)?.substringAfter('|')?.takeIf { it.isNotBlank() },
      lastErrorMillis = store.meta(KEY_LAST_ERROR)?.substringBefore('|')?.toLongOrNull(),
      deviceId = deviceId,
      encryption = encryption.state.value
    )
  }

  suspend fun resendAll() = syncNow(reconcile = true)

  private fun isE2eParams(record: SyncRecord) =
    record.collection == Collections.SETTINGS && record.id == SyncEncryption.RECORD_ID

  fun resetCursor() {
    scope.launch { syncFromStart() }
  }

  suspend fun syncFromStart() {
    mutex.withLock {
      store.setMeta(KEY_CURSOR, "0")
      syncLocked()
    }
  }

  suspend fun disconnect() {
    mutex.withLock {
      AppLog.info(TAG, "server disconnected")
      store.setMeta(KEY_URL, "")
      vault.write(VAULT_TOKEN, null)
      store.setMeta(KEY_CURSOR, "0")
      store.setMeta(KEY_DATASET, "")
      _state.value = SyncState.NotConfigured
    }
  }

  companion object {
    private const val KEY_RECONCILED = "sync_reconciled_v1"
    private const val KEY_DATASET = "sync_dataset"
    private const val KEY_USER = "sync_server_user"
    private const val KEY_LAST_OK = "sync_last_ok"
    private const val KEY_LAST_ERROR = "sync_last_error"
    private const val TAG = "sync"
    private const val BATCH_RECORDS = 200
    private const val BATCH_BYTES = 2_000_000
    const val KEY_URL = "server_url"
    const val KEY_TOKEN = "server_token"
    const val VAULT_TOKEN = "sync.token"
    const val KEY_CURSOR = "sync_cursor"
    const val KEY_OPTIONS = "sync_options"
  }
}

private typealias Outgoing = Triple<SyncRecord, Long, SyncRecord>
