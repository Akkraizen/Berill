package dev.akkraizen.berill.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.akkraizen.berill.core.db.Record
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.protocol.HybridClock
import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class PendingDeletion(val collection: String, val id: String, val stamp: String)

class LocalStore(private val db: BerillDatabase, private val clock: HybridClock) {
  private val q = db.recordsQueries
  private val json = Json { ignoreUnknownKeys = true }
  private val clocksSerializer = MapSerializer(String.serializer(), String.serializer())

  private val _localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

  val localChanges: SharedFlow<Unit> = _localChanges

  private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

  val changes: SharedFlow<Unit> = _changes

  private fun changed() {
    _localChanges.tryEmit(Unit)
    _changes.tryEmit(Unit)
  }

  private val _pendingDeletions = MutableStateFlow(emptyList<PendingDeletion>())
  val pendingDeletions = _pendingDeletions.asStateFlow()

  fun observe(collection: String): Flow<List<SyncRecord>> =
    q.selectByCollection(collection).asFlow().mapToList(Dispatchers.Default).map { rows ->
      rows.map(::toRecord)
    }

  fun observeCounts(): Flow<Map<String, Long>> =
    q.countByCollection().asFlow().mapToList(Dispatchers.Default).map { rows ->
      rows.associate { it.collection to it.total }
    }

  fun get(
    collection: String,
    id: String
  ): SyncRecord? = q.selectOne(collection, id).executeAsOneOrNull()?.let(::toRecord)

  fun list(collection: String): List<SyncRecord> = q.selectByCollection(collection).executeAsList().map(::toRecord)

  fun put(collection: String, id: String, fields: Map<String, JsonElement>) {
    db.transaction {
      val row = q.selectOne(collection, id).executeAsOneOrNull()
      val current = row?.let(::toRecord) ?: SyncRecord(collection, id)
      val changed = fields.filter { (k, v) -> current.fields[k] != v || current.deleted }
      if (changed.isEmpty()) return@transaction
      val stamp = clock.tick()
      val patch = SyncRecord(collection, id, JsonObject(changed), changed.keys.associateWith { stamp })
      write(current.mergeWith(patch), dirty = true, version = (row?.version ?: 0) + 1)
    }
    changed()
  }

  fun delete(collection: String, id: String) {
    var deletion: PendingDeletion? = null
    db.transaction {
      val row = q.selectOne(collection, id).executeAsOneOrNull() ?: return@transaction
      val current = toRecord(row)
      if (current.deleted) return@transaction
      val stamp = clock.tick()
      write(current.copy(deletedAt = stamp), dirty = true, version = row.version + 1)
      deletion = PendingDeletion(collection, id, stamp)
    }
    deletion?.let { item -> _pendingDeletions.update { it + item } }
    changed()
  }

  fun dismissDeletions(batch: List<PendingDeletion>) {
    _pendingDeletions.update { pending -> pending.filterNot { it in batch } }
  }

  fun undoDeletions(batch: List<PendingDeletion>) {
    db.transaction {
      for (item in batch) {
        val row = q.selectOne(item.collection, item.id).executeAsOneOrNull() ?: continue
        val current = toRecord(row)
        if (!current.deleted || current.deletedAt != item.stamp) continue
        val stamp = clock.tick()
        write(current.copy(clocks = current.fields.keys.associateWith { stamp }), dirty = true, version = row.version + 1)
      }
    }
    dismissDeletions(batch)
    changed()
  }

  fun restampAll() {
    db.transaction {
      for (row in q.selectAll().executeAsList()) {
        val current = toRecord(row)
        val stamp = clock.tick()
        var next = current.copy(clocks = current.fields.keys.associateWith { stamp })
        if (current.deleted) next = next.copy(deletedAt = clock.tick())
        write(next, dirty = true, version = row.version + 1)
      }
    }
    changed()
  }

  fun enqueueAllForSync() {
    db.transaction {
      for (row in q.selectAll().executeAsList()) {
        write(toRecord(row), dirty = true, version = row.version + 1)
      }
    }
  }
  fun countAll(): Long = q.countAll().executeAsOne()

  fun dirty(): List<Pair<SyncRecord, Long>> = q.selectDirty().executeAsList().map { toRecord(it) to it.version }

  fun applyRemote(records: List<SyncRecord>) {
    if (records.isEmpty()) return
    db.transaction {
      for (remote in records) {
        remote.latestClock?.let(clock::observe)
        val row = q.selectOne(remote.collection, remote.id).executeAsOneOrNull()
        val merged = row?.let { toRecord(it).mergeWith(remote) } ?: remote
        write(merged, dirty = row?.dirty == 1L, version = row?.version ?: 0)
      }
    }
    _changes.tryEmit(Unit)
  }

  fun markClean(record: SyncRecord, version: Long) = q.markClean(record.collection, record.id, version)

  fun meta(key: String): String? = q.getMeta(key).executeAsOneOrNull()

  fun setMeta(key: String, value: String) = q.setMeta(key, value)

  private fun write(r: SyncRecord, dirty: Boolean, version: Long) {
    q.upsert(
      collection = r.collection,
      id = r.id,
      fields = json.encodeToString(JsonObject.serializer(), r.fields),
      clocks = json.encodeToString(clocksSerializer, r.clocks),
      deleted_at = r.deletedAt,
      is_deleted = if (r.deleted) 1 else 0,
      dirty = if (dirty) 1 else 0,
      version = version
    )
  }

  private fun toRecord(row: Record) =
    SyncRecord(
      collection = row.collection,
      id = row.id,
      fields = json.decodeFromString(JsonObject.serializer(), row.fields),
      clocks = json.decodeFromString(clocksSerializer, row.clocks),
      deletedAt = row.deleted_at
    )
}
