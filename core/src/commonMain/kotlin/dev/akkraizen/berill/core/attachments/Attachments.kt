package dev.akkraizen.berill.core.attachments

import dev.akkraizen.berill.core.connections.*
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.secure.SecretVault
import dev.akkraizen.berill.core.sync.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

interface AttachmentCache {
  fun put(hash: String, bytes: ByteArray)
  fun get(hash: String): ByteArray?
  fun hashes(): List<String>
}
class MemoryAttachmentCache : AttachmentCache {
  private val data = mutableMapOf<String, ByteArray>()
  override fun put(hash: String, bytes: ByteArray) { data[hash] = bytes.copyOf() }
  override fun get(hash: String): ByteArray? = data[hash]?.copyOf()
  override fun hashes(): List<String> = data.keys.toList()
}
expect fun attachmentHash(bytes: ByteArray): String

class Attachments(val cache: AttachmentCache, private val store: LocalStore, private val vault: SecretVault, private val encryption: SyncEncryption) {
  private val http = createHttpClient()
  private val mutex = Mutex()
  private val downloadMutex = Mutex()
  companion object {
    private const val TAG = "attachments"
  }
  private fun pending(): Set<String> = store.meta("blobs.pending")?.split(',')?.filter { it.length == 64 }?.toSet().orEmpty()
  private fun pending(value: Set<String>) = store.setMeta("blobs.pending", value.sorted().joinToString(","))

  suspend fun put(bytes: ByteArray): String = mutex.withLock {
    if (bytes.size > 20 * 1024 * 1024) throw FeatureException(FailureReason.TOO_LARGE)
    val hash = attachmentHash(bytes)
    withContext(Dispatchers.Default) { cache.put(hash, bytes) }
    pending(pending() + hash)
    hash
  }

  suspend fun uploadPending() = mutex.withLock {
    val url = store.meta(SyncEngine.KEY_URL)?.takeIf { it.isNotBlank() } ?: return@withLock
    val token = vault.read(SyncEngine.VAULT_TOKEN) ?: return@withLock
    if (encryption.state.value == E2eState.Locked) return@withLock
    val mode = encryption.state.value.name
    if (store.meta("blobs.mode") != mode || store.meta(SyncEngine.KEY_RECONCILED) != "1") pending(pending() + cache.hashes())
    for (hash in pending()) {
      val bytes = withContext(Dispatchers.Default) { cache.get(hash) }
      if (bytes == null) {
        // Nothing can ever upload these bytes again; keeping the hash would block every sync.
        AppLog.warn(TAG, "attachment $hash is missing from the local cache; upload skipped")
        pending(pending() - hash)
        continue
      }
      val response = http.put("$url/api/blobs/$hash") {
        bearerAuth(token); contentType(ContentType.Application.OctetStream)
        setBody(encryption.encryptAttachment(bytes))
      }
      if (response.status == HttpStatusCode.PayloadTooLarge) {
        // A permanent rejection of one file must not stop records and other files from syncing.
        AppLog.warn(TAG, "server rejected attachment $hash (${bytes.size} bytes) as too large; upload skipped")
        pending(pending() - hash)
        continue
      }
      checkServiceStatus(response.status.value)
      pending(pending() - hash)
    }
    store.setMeta("blobs.mode", mode)
  }

  suspend fun get(hash: String): ByteArray? = try {
    require(hash.matches(Regex("[0-9a-f]{64}")))
    // Cached photos must not wait for a slow upload, even when offline or encryption is locked.
    withContext(Dispatchers.Default) { cache.get(hash) } ?: download(hash)
  }
  catch (e: kotlinx.coroutines.CancellationException) { throw e }
  catch (e: Exception) { AppLog.warn(TAG, "download failed", e); throw e }

  private suspend fun download(hash: String): ByteArray? = downloadMutex.withLock {
    require(hash.matches(Regex("[0-9a-f]{64}")))
    withContext(Dispatchers.Default) { cache.get(hash) }?.let { return@withLock it }
    val url = store.meta(SyncEngine.KEY_URL)?.takeIf { it.isNotBlank() } ?: return@withLock null
    val token = vault.read(SyncEngine.VAULT_TOKEN) ?: return@withLock null
    if (encryption.state.value == E2eState.Locked) throw FeatureException(FailureReason.ENCRYPTION_LOCKED)
    val response = http.get("$url/api/blobs/$hash") { bearerAuth(token) }
    if (response.status == HttpStatusCode.NotFound) return@withLock null
    checkServiceStatus(response.status.value)
    val bytes = encryption.decryptAttachment(response.body<ByteArray>()) ?: throw FeatureException(FailureReason.CORRUPT_ATTACHMENT)
    if (attachmentHash(bytes) != hash) throw FeatureException(FailureReason.CORRUPT_ATTACHMENT)
    withContext(Dispatchers.Default) { cache.put(hash, bytes) }
    bytes
  }
}
