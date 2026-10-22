package dev.akkraizen.tessera.server

import dev.akkraizen.tessera.protocol.SyncRecord
import dev.akkraizen.tessera.protocol.SyncRequest
import dev.akkraizen.tessera.protocol.SyncResponse
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

class SyncRepository(private val db: Database) {
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

      val page = Records.selectAll()
        .where { (Records.userId eq user) and (Records.rev greater request.since) }
        .orderBy(Records.rev, SortOrder.ASC)
        .limit(PAGE + 1)
        .map(::toRecord)
      val records = page.take(PAGE)
      SyncResponse(
        records = records,
        cursor = records.lastOrNull()?.rev ?: maxOf(request.since, 0),
        hasMore = page.size > PAGE
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
