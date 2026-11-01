package dev.akkraizen.berill.server

import dev.akkraizen.berill.protocol.HybridClock
import dev.akkraizen.berill.protocol.SyncRecord
import dev.akkraizen.berill.protocol.SyncRequest
import dev.akkraizen.berill.protocol.SyncResponse
import java.util.UUID
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

/** A device sent stamps far ahead of the server's time: its clock is wrong. */
class ClockSkewException(val record: String) : RuntimeException("clock_skew: $record")

/**
 * Rejects stamps more than [HybridClock.MAX_DRIFT_MILLIS] ahead of [now] unless they continue a
 * future time the server already stores for that record (an edit of data written earlier by a device
 * with a wrong clock). Such stamps would otherwise win against every later edit on every device.
 */
fun checkClockSkew(change: SyncRecord, existing: SyncRecord?, now: Long) {
  val limit = now + HybridClock.MAX_DRIFT_MILLIS
  val known = (existing?.clocks?.values.orEmpty() + listOfNotNull(existing?.deletedAt)).mapNotNull { HybridClock.decode(it)?.first }.toSet()
  val stamps = change.clocks.values + listOfNotNull(change.deletedAt)
  if (stamps.any { stamp -> HybridClock.decode(stamp)?.first?.let { it > limit && it !in known } == true }) {
    throw ClockSkewException("${change.collection}/${change.id}")
  }
}

class SyncRepository(private val db: Database, private val now: () -> Long = System::currentTimeMillis) {
  private val json = Json { ignoreUnknownKeys = true }
  private val clocksSerializer = MapSerializer(String.serializer(), String.serializer())

  fun sync(user: String, request: SyncRequest): SyncResponse =
    transaction(db) {
      var rev = Counters.selectAll()
        .where { Counters.userId eq user }
        .forUpdate()
        .singleOrNull()
        ?.get(Counters.rev)
        ?: run {
          Counters.insert {
            it[userId] = user
            it[Counters.rev] = 0L
          }
          0L
        }

      for (change in request.changes) {
        val existing = Records.selectAll()
          .where {
            (Records.userId eq user) and
              (Records.collection eq change.collection) and
              (Records.recordId eq change.id)
          }
          .singleOrNull()
          ?.let(::toRecord)
        checkClockSkew(change, existing, now())
        val merged = existing?.mergeWith(change) ?: change.copy(rev = 0)
        if (merged.sameContentAs(existing)) continue
        rev += 1
        Records.upsert {
          it[userId] = user
          it[collection] = merged.collection
          it[recordId] = merged.id
          it[realms] = json.encodeToString(JsonObject.serializer(), merged.fields)
          it[clocks] = json.encodeToString(clocksSerializer, merged.clocks)
          it[deletedAt] = merged.deletedAt
          it[Records.rev] = rev
        }
      }
      Counters.update({ Counters.userId eq user }) { it[Counters.rev] = rev }

      val dataset = Datasets.selectAll()
        .where { Datasets.userId eq user }
        .singleOrNull()
        ?.get(Datasets.dataset)
        ?: UUID.randomUUID().toString().also { id ->
          Datasets.insert {
            it[userId] = user
            it[Datasets.dataset] = id
          }
        }

      val page = Records.selectAll()
        .where { (Records.userId eq user) and (Records.rev greater request.since) }
        .orderBy(Records.rev, SortOrder.ASC)
        .limit(PAGE + 1)
        .map(::toRecord)
      val records = page.take(PAGE)
      SyncResponse(
        records = records,
        cursor = records.lastOrNull()?.rev ?: maxOf(request.since, 0),
        hasMore = page.size > PAGE,
        dataset = dataset,
        user = user
      )
    }

  private fun toRecord(row: ResultRow) =
    SyncRecord(
      collection = row[Records.collection],
      id = row[Records.recordId],
      fields = json.decodeFromString(JsonObject.serializer(), row[Records.realms]),
      clocks = json.decodeFromString(clocksSerializer, row[Records.clocks]),
      deletedAt = row[Records.deletedAt],
      rev = row[Records.rev]
    )

  private companion object {
    const val PAGE = 2000
  }
}
