package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sun.net.httpserver.HttpServer
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.secure.MemoryVault
import dev.akkraizen.berill.core.sync.SyncEncryption
import dev.akkraizen.berill.core.sync.SyncEngine
import dev.akkraizen.berill.core.sync.SyncOptions
import dev.akkraizen.berill.core.sync.SyncState
import dev.akkraizen.berill.protocol.HybridClock
import dev.akkraizen.berill.protocol.SyncRecord
import dev.akkraizen.berill.protocol.SyncRequest
import dev.akkraizen.berill.protocol.SyncResponse
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SyncRecoveryTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val vault = MemoryVault()
  private val uploaded = mutableListOf<SyncRecord>()
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  private val store: LocalStore
  private var reject = false
  private var incoming = emptyList<SyncRecord>()
  private var dataset: String? = null

  init {
    BerillDatabase.Schema.create(driver)
    store = LocalStore(BerillDatabase(driver), HybridClock("local") { 100L })
    server.createContext("/api/sync") { exchange ->
      val request = Json.decodeFromString<SyncRequest>(exchange.requestBody.bufferedReader().use { it.readText() })
      val status = if (reject) 503 else 200
      if (!reject) uploaded.addAll(request.changes)
      val records = incoming.filter { it.rev > request.since }
      val bytes = Json.encodeToString(SyncResponse(records, records.lastOrNull()?.rev ?: request.since, dataset = dataset)).encodeToByteArray()
      exchange.responseHeaders.set("Content-Type", "application/json")
      exchange.sendResponseHeaders(status, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    store.setMeta(SyncEngine.KEY_URL, "http://127.0.0.1:${server.address.port}")
    vault.write(SyncEngine.VAULT_TOKEN, "test-token")
  }

  @AfterTest
  fun close() {
    server.stop(0)
    scope.cancel()
    driver.close()
  }

  private fun engine() = SyncEngine(store, "local", scope, SyncEncryption(store, vault), vault)

  private fun cleanRecords(): List<SyncRecord> {
    for (collection in listOf("books", "habits", "food_entries", "workout_logs", "notes")) {
      store.put(collection, "item", mapOf("title" to JsonPrimitive(collection)))
    }
    store.delete("notes", "item")
    val records = store.dirty()
    records.forEach { (record, version) -> store.markClean(record, version) }
    store.setMeta(SyncEngine.KEY_CURSOR, "13")
    return records.map { it.first }
  }

  @Test
  fun recoversPreviouslyAcknowledgedCollectionsWithoutChangingClocksOrTombstones() = runBlocking {
    val original = cleanRecords()
    val engine = engine()
    engine.syncNow()
    assertTrue(engine.state.value is SyncState.Done)
    assertEquals(original.toSet(), uploaded.toSet())
    assertTrue(store.dirty().isEmpty())
    uploaded.clear()
    engine.syncNow()
    assertTrue(uploaded.isEmpty(), "Regular incremental sync must not resend clean records")
  }

  @Test
  fun recoveryRespectsExcludedCollections() = runBlocking {
    cleanRecords()
    store.setMeta(SyncEngine.KEY_OPTIONS, SyncOptions(excludedCollections = setOf("books")).encode())
    engine().syncNow()
    assertEquals(setOf("habits", "food_entries", "workout_logs", "notes"), uploaded.map { it.collection }.toSet())
    assertEquals(listOf("books"), store.dirty().map { it.first.collection })
  }

  @Test
  fun failedRecoveryIsRetried() = runBlocking {
    val original = cleanRecords()
    val engine = engine()
    reject = true
    engine.syncNow()
    assertTrue(engine.state.value is SyncState.Failed)
    reject = false
    engine.syncNow()
    assertEquals(original.toSet(), uploaded.toSet())
    assertTrue(store.dirty().isEmpty())
  }

  @Test
  fun manualRecoveryRepairsServerDataLostAfterInitialSync() = runBlocking {
    val original = cleanRecords()
    val engine = engine()
    engine.syncNow()
    uploaded.clear()
    engine.syncNow(reconcile = true)
    assertEquals(original.toSet(), uploaded.toSet())
  }

  @Test
  fun reconciliationKeepsNewerRemoteFields() = runBlocking {
    val original = cleanRecords().first { it.collection == "books" }
    val newer = original.copy(
      fields = JsonObject(mapOf("title" to JsonPrimitive("newer remote title"))),
      clocks = mapOf("title" to HybridClock.encode(200L, 0, "remote")),
      rev = 1L
    )
    incoming = listOf(newer)
    engine().syncNow()
    assertEquals(newer.fields, store.get("books", "item")!!.fields)
    assertEquals(newer.clocks, uploaded.first { it.collection == "books" }.clocks)
  }

  @Test
  fun recreatedServerDatabaseTriggersFullResend() = runBlocking {
    val original = cleanRecords()
    dataset = "first"
    val engine = engine()
    engine.syncNow()
    uploaded.clear()
    engine.syncNow()
    assertTrue(uploaded.isEmpty())
    dataset = "second"
    engine.syncNow()
    assertTrue(engine.state.value is SyncState.Done)
    assertEquals(original.toSet(), uploaded.toSet())
  }
}
