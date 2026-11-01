package dev.akkraizen.berill.core.sync

import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Serializable
data class SyncConflict(val id: String, val local: SyncRecord, val remote: SyncRecord, val merged: SyncRecord, val fields: List<String>, val deletion: Boolean, val at: Long)
enum class ConflictChoice { LOCAL, REMOTE, MERGED }

class ConflictJournal(private val read: (String) -> String?, private val storeValue: (String, String) -> Unit) {
  private val lock = kotlinx.atomicfu.locks.SynchronizedObject()
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private fun load(): List<SyncConflict> = read(KEY)?.takeIf { it.isNotBlank() }?.let { json.decodeFromString(ListSerializer(SyncConflict.serializer()), it) } ?: emptyList()
  private val mutable = MutableStateFlow(load())
  val state = mutable.asStateFlow()
  /** Set by every write; [reload] skips parsing the journal when nothing changed. */
  private var stale = false
  private fun write(key: String, value: String) { storeValue(key, value); synchronized(lock) { stale = true } }
  fun reload() = synchronized(lock) {
    if (!stale) return@synchronized
    stale = false
    mutable.value = load()
  }
  internal fun capture(local: SyncRecord, remote: SyncRecord, merged: SyncRecord, base: SyncRecord?) {
    if (base == null) return
    val fields = (local.fields.keys intersect remote.fields.keys).filterNot { it in METADATA || it.startsWith("__berill_") }.filter { key ->
      local.clocks[key].orEmpty() > base.clocks[key].orEmpty() && remote.clocks[key].orEmpty() > base.clocks[key].orEmpty() &&
        local.fields[key] != base.fields[key] && remote.fields[key] != base.fields[key] && local.fields[key] != remote.fields[key]
    }
    val deletion = local.deleted != remote.deleted &&
      changed(local, base) && changed(remote, base)
    if (fields.isEmpty() && !deletion) return
    val current = load()
    if (current.any { it.local.sameContentAs(local) && it.remote.sameContentAs(remote) }) return
    val conflict = SyncConflict(Uuid.random().toString(), local, remote, merged, fields, deletion, Clock.System.now().toEpochMilliseconds())
    write(KEY, json.encodeToString(ListSerializer(SyncConflict.serializer()), current + conflict))
  }
  internal fun draft(existingId: String?, local: SyncRecord, remote: SyncRecord, merged: SyncRecord, fields: List<String>, deletion: Boolean): String {
    val current = load()
    val id = current.firstOrNull { it.id == existingId && it.local.collection == local.collection && it.local.id == local.id }?.id ?: Uuid.random().toString()
    val value = SyncConflict(id, local, remote, merged, fields, deletion, Clock.System.now().toEpochMilliseconds())
    write(KEY, json.encodeToString(ListSerializer(SyncConflict.serializer()), current.filterNot { it.id == id } + value))
    return id
  }
  internal fun forget(collection: String, id: String) { write(KEY, json.encodeToString(ListSerializer(SyncConflict.serializer()), load().filterNot { it.local.collection == collection && it.local.id == id })) }
  internal fun get(id: String): SyncConflict? = load().firstOrNull { it.id == id }
  internal fun remove(id: String) { write(KEY, json.encodeToString(ListSerializer(SyncConflict.serializer()), load().filterNot { it.id == id })) }
  private fun changed(record: SyncRecord, base: SyncRecord): Boolean =
    (record.deleted != base.deleted && record.latestClock.orEmpty() > base.latestClock.orEmpty()) ||
      payload(record).any { (key, value) -> value != base.fields[key] && record.clocks[key].orEmpty() > base.clocks[key].orEmpty() }
  private fun payload(record: SyncRecord) = record.fields.filterKeys { it !in METADATA && !it.startsWith("__berill_") }
  companion object {
    private const val KEY = "sync.conflicts.v1"
    private val METADATA = setOf("updatedAt", "updated_at", "createdAt", "created_at", "dirty", "etag")
  }
}
