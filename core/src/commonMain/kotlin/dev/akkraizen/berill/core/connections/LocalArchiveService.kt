package dev.akkraizen.berill.core.connections

import dev.akkraizen.berill.core.attachments.Attachments
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A validated archive waiting for the user's confirmation. */
class PendingArchiveImport internal constructor(val records: List<SyncRecord>, internal val attachments: Map<String, ByteArray>)

/**
 * Local export and import of all records and attachments (Settings → Data). Keeps the file format
 * and validation in one place so the settings screen only handles state and dialogs.
 */
class LocalArchiveService(private val store: LocalStore, private val attachments: Attachments) {
  /** Builds the ZIP archive. Fails with [FailureReason.MISSING_ATTACHMENT] if a referenced file is unavailable. */
  suspend fun export(): ByteArray {
    val records = store.records()
    val files = referencedAttachments(records).associateWith { hash ->
      attachments.get(hash) ?: throw FeatureException(FailureReason.MISSING_ATTACHMENT)
    }
    return withContext(Dispatchers.Default) { encodeLocalArchive(records, files) }
  }

  /** Validates an archive without changing any data. */
  suspend fun read(bytes: ByteArray): PendingArchiveImport {
    val (records, files) = withContext(Dispatchers.Default) {
      try {
        decodeLocalArchive(bytes) to decodeArchiveAttachments(bytes)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        throw FeatureException(FailureReason.INVALID_DATA, e)
      }
    }
    val missing = referencedAttachments(records).filter { it !in files && attachments.cache.get(it) == null }
    if (missing.isNotEmpty()) throw FeatureException(FailureReason.MISSING_ATTACHMENT)
    return PendingArchiveImport(records, files)
  }

  /** Stores the attachments, merges the records with the usual conflict rules and queues them for sync. */
  suspend fun apply(import: PendingArchiveImport) {
    for (bytes in import.attachments.values) attachments.put(bytes)
    store.applyRemote(import.records)
    store.enqueueAllForSync()
  }
}

private val ATTACHMENT_REFERENCE = Regex("blob:([0-9a-f]{64})")

/** Hashes of all attachments referenced as `blob:<sha256>` anywhere in the records' fields. */
fun referencedAttachments(records: List<SyncRecord>): Set<String> {
  val result = mutableSetOf<String>()
  fun visit(element: JsonElement) {
    when (element) {
      is JsonObject -> element.values.forEach(::visit)
      is JsonArray -> element.forEach(::visit)
      is JsonPrimitive -> if (element.isString) ATTACHMENT_REFERENCE.findAll(element.content).forEach { result += it.groupValues[1] }
    }
  }
  records.forEach { visit(it.fields) }
  return result
}
