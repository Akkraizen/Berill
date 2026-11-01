package dev.akkraizen.berill.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.akkraizen.berill.core.db.Record
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.sync.ConflictChoice
import dev.akkraizen.berill.core.sync.ConflictJournal
import dev.akkraizen.berill.protocol.Collections
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject

data class DraftMergeResult(val record: SyncRecord?, val baseline: SyncRecord?, val conflictId: String?, val fields: Set<String> = emptySet(), val deletion: Boolean = false)

data class PendingDeletion(val collection: String, val id: String, val stamp: String)

class LocalStore(private val db: BerillDatabase, private val clock: HybridClock) {
  private val q = db.recordsQueries
  private val json = Json { ignoreUnknownKeys = true }
  val conflicts = ConflictJournal(::meta, ::setMeta)
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
      val stamp = stampFor(current, changed.keys)
      val patch = SyncRecord(collection, id, JsonObject(changed), changed.keys.associateWith { stamp })
      write(current.mergeWith(patch), dirty = true, version = (row?.version ?: 0) + 1)
    }
    changed()
  }

  fun putIfCurrent(collection: String, id: String, field: String, expected: JsonElement, value: JsonElement): Boolean {
    var updated = false
    db.transaction {
      val row = q.selectOne(collection, id).executeAsOneOrNull() ?: return@transaction
      val current = toRecord(row)
      if (current.deleted || current.fields[field] != expected || expected == value) return@transaction
      current.latestClock?.let(clock::observe)
      val patch = SyncRecord(collection, id, JsonObject(mapOf(field to value)), mapOf(field to stampFor(current, setOf(field))))
      write(current.mergeWith(patch), dirty = true, version = row.version + 1)
      updated = true
    }
    if (updated) changed()
    return updated
  }

  fun delete(collection: String, id: String) {
    var deletion: PendingDeletion? = null
    db.transaction {
      val row = q.selectOne(collection, id).executeAsOneOrNull() ?: return@transaction
      val current = toRecord(row)
      if (current.deleted) return@transaction
      val stamp = stampFor(current, current.clocks.keys)
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
        val stamp = stampFor(current, current.clocks.keys)
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
        val stamp = stampFor(current, current.clocks.keys)
        var next = current.copy(clocks = current.fields.keys.associateWith { stamp })
        if (current.deleted) next = next.copy(deletedAt = clock.stampAbove(listOf(stamp)))
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
  fun records(): List<SyncRecord> = q.selectAll().executeAsList().map(::toRecord)

  fun deletedRecords(): List<SyncRecord> =
    q.selectDeleted().executeAsList().map(::toRecord).filter { it.deleted && it.fields[PURGED] != JsonPrimitive(true) }

  fun restore(collection: String, id: String) {
    val record = get(collection, id)?.takeIf { it.deleted && it.fields[PURGED] != JsonPrimitive(true) } ?: return
    record.latestClock?.let(clock::observe)
    undoDeletions(listOf(PendingDeletion(collection, id, record.deletedAt!!)))
  }

  /** Erases deleted content while retaining a sync tombstone against stale replicas. */
  fun purgeDeleted(records: List<SyncRecord>) {
    db.transaction {
      for (record in records) {
        val row = q.selectOne(record.collection, record.id).executeAsOneOrNull() ?: continue
        val current = toRecord(row)
        if (!current.deleted || current.deletedAt != record.deletedAt) continue
        current.latestClock?.let(clock::observe)
        val stamp = stampFor(current, current.clocks.keys)
        val fields = current.fields.mapValues { JsonNull } + (PURGED to JsonPrimitive(true))
        write(current.copy(fields = JsonObject(fields), clocks = fields.keys.associateWith { stamp }, deletedAt = clock.stampAbove(listOf(stamp))),
          dirty = true, version = row.version + 1)
        setMeta(baseKey(record.collection, record.id), json.encodeToString(SyncRecord.serializer(), get(record.collection, record.id)!!))
        conflicts.forget(record.collection, record.id)
        _pendingDeletions.update { pending -> pending.filterNot { it.collection == record.collection && it.id == record.id } }
      }
    }
    conflicts.reload()
    changed()
  }

  fun countAll(): Long = q.countAll().executeAsOne()

  /**
   * Gives local, not yet sent changes a current stamp if they were made while this device's clock ran
   * far ahead. Otherwise such a field would win against every later edit on all devices. Returns the
   * number of records changed.
   */
  fun restampFutureChanges(): Int {
    var count = 0
    db.transaction {
      for (row in q.selectDirty().executeAsList()) {
        val record = toRecord(row)
        // Successors of future stamps the server already has (see HybridClock.stampAbove) stay as they are.
        val base = meta(baseKey(record.collection, record.id))?.let { json.decodeFromString(SyncRecord.serializer(), it) }
        val known = (base?.clocks?.values.orEmpty() + listOfNotNull(base?.deletedAt)).mapNotNull { HybridClock.decode(it)?.first }.toSet()
        fun ownFuture(stamp: String) = clock.isFromFuture(stamp) && HybridClock.decode(stamp)?.first !in known
        val future = record.clocks.filterValues(::ownFuture).keys
        val futureDeletion = record.deletedAt?.let(::ownFuture) == true
        if (future.isEmpty() && !futureDeletion) continue
        val stamp = clock.tick()
        val next = record.copy(
          clocks = record.clocks + future.associateWith { stamp },
          deletedAt = if (futureDeletion) clock.tick() else record.deletedAt,
        )
        write(next, dirty = true, version = row.version + 1)
        count++
      }
    }
    return count
  }

  fun dirty(): List<Pair<SyncRecord, Long>> = q.selectDirty().executeAsList().map { toRecord(it) to it.version }

  fun applyRemote(records: List<SyncRecord>) {
    if (records.isEmpty()) return
    db.transaction {
      for (remote in records) {
        remote.latestClock?.let(clock::observe)
        val row = q.selectOne(remote.collection, remote.id).executeAsOneOrNull()
        val local = row?.let(::toRecord)
        val merged = local?.mergeWith(remote) ?: remote
        if (remote.collection != Collections.SETTINGS && row?.dirty == 1L && local != null) {
          val base = meta(baseKey(remote.collection, remote.id))?.let { json.decodeFromString(SyncRecord.serializer(), it) }
          conflicts.capture(local, remote, merged, base)
        }
        write(merged, dirty = row?.dirty == 1L, version = row?.version ?: 0)
        if (remote.collection != Collections.SETTINGS) {
          val known = meta(baseKey(remote.collection, remote.id))?.let { json.decodeFromString(SyncRecord.serializer(), it) }
          setMeta(baseKey(remote.collection, remote.id), json.encodeToString(SyncRecord.serializer(), known?.mergeWith(remote) ?: remote))
        }
      }
    }
    conflicts.reload()
    _changes.tryEmit(Unit)
  }

  fun markClean(record: SyncRecord, version: Long) {
    db.transaction {
      val row = q.selectOne(record.collection, record.id).executeAsOneOrNull()
      q.markClean(record.collection, record.id, version)
      if (row?.version == version && record.sameContentAs(toRecord(row)) && record.collection != dev.akkraizen.berill.protocol.Collections.SETTINGS)
        setMeta(baseKey(record.collection, record.id), json.encodeToString(SyncRecord.serializer(), record))
    }
  }

  /** A stamp that wins over the current stamps of [fields] and the record's tombstone. */
  private fun stampFor(current: SyncRecord, fields: Collection<String>): String =
    clock.stampAbove(fields.mapNotNull { current.clocks[it] } + listOfNotNull(current.deletedAt))

  private fun baseKey(collection: String, id: String) = "sync.base.$collection/$id"

  fun mergeDraft(collection: String, id: String, base: SyncRecord?, desired: JsonObject, existingConflict: String? = null): DraftMergeResult {
    var result = DraftMergeResult(null, base, existingConflict)
    var wrote = false
    db.transaction {
      val row = q.selectOne(collection, id).executeAsOneOrNull()
      val current = row?.let(::toRecord) ?: SyncRecord(collection, id)
      val delta = desired.filter { (key, value) -> key != "updatedAt" && base?.fields?.get(key) != value }
      if (delta.isEmpty()) {
        existingConflict?.let(conflicts::remove)
        result = DraftMergeResult(row?.let(::toRecord), row?.let(::toRecord), null)
        return@transaction
      }
      current.latestClock?.let(clock::observe)
      val collision = delta.keys.filter { current.fields[it] != base?.fields?.get(it) && current.fields[it] != desired[it] }.toSet()
      val deletion = row != null && current.deleted
      val safe = if (deletion) emptyMap() else delta.filter { (key, value) -> key !in collision && current.fields[key] != value }
      var merged = current
      if (safe.isNotEmpty()) {
        val patch = safe + desired.filterKeys { it == "updatedAt" }
        val stamp = stampFor(current, patch.keys)
        merged = current.copy(fields = JsonObject(current.fields + patch), clocks = current.clocks + patch.keys.associateWith { stamp })
        write(merged, dirty = true, version = (row?.version ?: 0) + 1)
        wrote = true
      }
      if (collision.isNotEmpty() || deletion) {
        val stamp = stampFor(merged, desired.keys)
        val local = merged.copy(fields = JsonObject(merged.fields + desired), clocks = merged.clocks + desired.keys.associateWith { stamp }, deletedAt = null)
        val conflictId = conflicts.draft(existingConflict, local, current, merged, collision.toList(), deletion)
        val baselineFields = merged.fields.toMutableMap()
        val baselineClocks = merged.clocks.toMutableMap()
        collision.forEach { key ->
          if (base?.fields?.containsKey(key) == true) baselineFields[key] = base.fields.getValue(key) else baselineFields.remove(key)
          base?.clocks?.get(key)?.let { baselineClocks[key] = it } ?: baselineClocks.remove(key)
        }
        val baseline = if (deletion) base else merged.copy(fields = JsonObject(baselineFields), clocks = baselineClocks)
        result = DraftMergeResult(merged, baseline, conflictId, collision, deletion)
      } else {
        existingConflict?.let(conflicts::remove)
        result = DraftMergeResult(merged, merged, null)
      }
    }
    conflicts.reload()
    if (wrote) changed()
    return result
  }

  fun resolveConflict(id: String, choice: ConflictChoice): Boolean {
    var resolved = false
    db.transaction {
      val conflict = conflicts.get(id) ?: return@transaction
      val collection = conflict.local.collection
      val recordId = conflict.local.id
      val row = q.selectOne(collection, recordId).executeAsOneOrNull() ?: return@transaction
      val current = toRecord(row)
      if (choice != ConflictChoice.MERGED) {
        val fresh = if (conflict.deletion) current.sameContentAs(conflict.merged) else conflict.fields.all {
          current.fields[it] == conflict.merged.fields[it] && current.clocks[it] == conflict.merged.clocks[it]
        } && current.deleted == conflict.merged.deleted
        if (!fresh) return@transaction
        val selected = if (choice == ConflictChoice.LOCAL) conflict.local else conflict.remote
        current.latestClock?.let(clock::observe)
        conflict.local.latestClock?.let(clock::observe)
        conflict.remote.latestClock?.let(clock::observe)
        val stamp = stampFor(current, current.clocks.keys)
        val keys = if (conflict.deletion) selected.fields.keys else conflict.fields.toSet()
        val fields = keys.associateWith { selected.fields[it] ?: JsonNull }.toMutableMap()
        if (!selected.deleted) {
          val timestamp = current.fields["updatedAt"] as? JsonPrimitive
          if (timestamp != null && !timestamp.isString && timestamp.content.toLongOrNull() != null) fields["updatedAt"] = JsonPrimitive(kotlin.time.Clock.System.now().toEpochMilliseconds())
          val flag = current.fields["dirty"] as? JsonPrimitive
          if (flag != null && !flag.isString && flag.content in setOf("true", "false")) fields["dirty"] = JsonPrimitive(true)
        }
        val next = current.copy(fields = JsonObject(current.fields + fields), clocks = current.clocks + fields.keys.associateWith { stamp },
          deletedAt = if (conflict.deletion) { if (selected.deleted) stamp else null } else current.deletedAt)
        write(next, dirty = true, version = row.version + 1)
      }
      conflicts.remove(id)
      resolved = true
    }
    conflicts.reload()
    if (resolved) changed()
    return resolved
  }

  fun createIfAbsent(collection: String, id: String, fields: JsonObject): Boolean {
    var created = false
    db.transaction {
      if (q.selectOne(collection, id).executeAsOneOrNull() != null) return@transaction
      val stamp = clock.tick()
      write(SyncRecord(collection, id, fields, fields.keys.associateWith { stamp }), dirty = true, version = 1)
      created = true
    }
    if (created) changed()
    return created
  }

  fun mutateIfCurrent(collection: String, id: String, expected: JsonObject, patch: JsonObject?, delete: Boolean): Boolean {
    var updated = false
    var deletion: PendingDeletion? = null
    db.transaction {
      val row = q.selectOne(collection, id).executeAsOneOrNull() ?: return@transaction
      val current = toRecord(row)
      if (current.deleted || current.fields != expected) return@transaction
      current.latestClock?.let(clock::observe)
      val stamp = stampFor(current, current.clocks.keys)
      val next = if (delete) current.copy(deletedAt = stamp) else {
        val changed = patch.orEmpty().filter { (key, value) -> current.fields[key] != value }
        current.copy(fields = JsonObject(current.fields + changed), clocks = current.clocks + changed.keys.associateWith { stamp })
      }
      write(next, dirty = true, version = row.version + 1)
      if (delete) deletion = PendingDeletion(collection, id, stamp)
      updated = true
    }
    deletion?.let { item -> _pendingDeletions.update { it + item } }
    if (updated) changed()
    return updated
  }

  fun meta(key: String): String? = q.getMeta(key).executeAsOneOrNull()

  fun setMeta(key: String, value: String) = q.setMeta(key, value)

  private fun write(r: SyncRecord, dirty: Boolean, version: Long) {
    if (dirty && r.collection != Collections.SETTINGS) {
      val row = q.selectOne(r.collection, r.id).executeAsOneOrNull()
      if (row?.dirty != 1L || meta(baseKey(r.collection, r.id)) == null) {
        val base = row?.let(::toRecord) ?: SyncRecord(r.collection, r.id)
        setMeta(baseKey(r.collection, r.id), json.encodeToString(SyncRecord.serializer(), base))
      }
    }
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

  private companion object {
    /** Marks a deleted record whose content was erased from the trash; the tombstone stays for sync. */
    const val PURGED = "__berill_purged"
  }
}
