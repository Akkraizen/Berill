package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sun.net.httpserver.HttpServer
import dev.akkraizen.berill.core.attachments.*
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.secure.MemoryVault
import dev.akkraizen.berill.core.sync.*
import dev.akkraizen.berill.protocol.HybridClock
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.test.*

class AttachmentCacheTest {
  @Test fun diskCacheSurvivesRestartAndMemoryCopiesCannotBeMutated() {
    val dir = Files.createTempDirectory("photo-cache").toFile()
    try {
      val bytes = "photo".encodeToByteArray()
      val hash = attachmentHash(bytes)
      val cache = FileAttachmentCache(dir)
      cache.put(hash, bytes)
      bytes[0] = 0
      val first = cache.get(hash)!!
      first[0] = 0
      assertEquals("photo", cache.get(hash)!!.decodeToString())
      assertEquals("photo", FileAttachmentCache(dir).get(hash)!!.decodeToString())
    } finally { dir.deleteRecursively() }
  }

  @Test fun memoryIsBoundedAndCorruptDiskDataIsRejected() {
    val dir = Files.createTempDirectory("photo-lru").toFile()
    try {
      val cache = FileAttachmentCache(dir, memoryLimitBytes = 6)
      val first = "photo1".encodeToByteArray()
      val hash = attachmentHash(first)
      cache.put(hash, first)
      java.io.File(dir, hash).writeText("corrupt")
      assertContentEquals(first, cache.get(hash)) // Memory hit, no disk read.
      val second = "photo2".encodeToByteArray()
      cache.put(attachmentHash(second), second) // Evicts first from memory only.
      assertNull(cache.get(hash))
    } finally { dir.deleteRecursively() }
  }

  @Test fun cachedPhotoDoesNotWaitForBlockedUploadAndWorksWithoutServer() = runBlocking {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val store = LocalStore(BerillDatabase(driver), HybridClock("cache-test") { 0L })
    val vault = MemoryVault()
    val encryption = SyncEncryption(store, vault)
    val service = Attachments(MemoryAttachmentCache(), store, vault, encryption)
    val bytes = "cached photo".encodeToByteArray()
    val hash = service.put(bytes)
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { call ->
      call.requestBody.readBytes()
      started.countDown()
      release.await(10, TimeUnit.SECONDS)
      call.sendResponseHeaders(204, -1)
      call.close()
    }
    server.start()
    store.setMeta(SyncEngine.KEY_URL, "http://127.0.0.1:${server.address.port}")
    vault.write(SyncEngine.VAULT_TOKEN, "test-token")
    val upload = launch(Dispatchers.Default) { service.uploadPending() }
    try {
      assertTrue(withContext(Dispatchers.IO) { started.await(10, TimeUnit.SECONDS) })
      assertContentEquals(bytes, withTimeout(1000) { service.get(hash) })
    } finally { release.countDown(); upload.join(); server.stop(0) }
    try {
      store.setMeta(SyncEngine.KEY_URL, "")
      assertContentEquals(bytes, service.get(hash))
    } finally { driver.close() }
  }

  @Test fun missingOrRejectedAttachmentsDoNotBlockOtherUploads() = runBlocking {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val store = LocalStore(BerillDatabase(driver), HybridClock("upload-test") { 0L })
    val vault = MemoryVault()
    val cache = MemoryAttachmentCache()
    val service = Attachments(cache, store, vault, SyncEncryption(store, vault))
    val large = service.put("too large".encodeToByteArray())
    val small = service.put("small".encodeToByteArray())
    val missing = "f".repeat(64)
    store.setMeta("blobs.pending", listOf(large, small, missing).sorted().joinToString(","))
    val uploaded = java.util.concurrent.CopyOnWriteArrayList<String>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { call ->
      val hash = call.requestURI.path.substringAfterLast('/')
      call.requestBody.readBytes()
      if (hash == large) call.sendResponseHeaders(413, -1) else { uploaded += hash; call.sendResponseHeaders(204, -1) }
      call.close()
    }
    server.start()
    try {
      store.setMeta(SyncEngine.KEY_URL, "http://127.0.0.1:${server.address.port}")
      store.setMeta(SyncEngine.KEY_RECONCILED, "1")
      store.setMeta("blobs.mode", SyncEncryption(store, vault).state.value.name)
      vault.write(SyncEngine.VAULT_TOKEN, "test-token")
      service.uploadPending()
      assertEquals(listOf(small), uploaded.toList())
      assertEquals("", store.meta("blobs.pending"))
    } finally { server.stop(0); driver.close() }
  }

  @Test fun afterReconciliationOnlyFilesTheServerLacksAreUploaded() = runBlocking {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val store = LocalStore(BerillDatabase(driver), HybridClock("missing-test") { 0L })
    val vault = MemoryVault()
    val cache = MemoryAttachmentCache()
    val service = Attachments(cache, store, vault, SyncEncryption(store, vault))
    val kept = attachmentHash("on server".encodeToByteArray()).also { cache.put(it, "on server".encodeToByteArray()) }
    val lost = attachmentHash("lost".encodeToByteArray()).also { cache.put(it, "lost".encodeToByteArray()) }
    val uploaded = java.util.concurrent.CopyOnWriteArrayList<String>()
    var legacyServer = false
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/api/blobs/missing") { call ->
      call.requestBody.readBytes()
      if (legacyServer) { call.sendResponseHeaders(404, -1); call.close(); return@createContext }
      val body = """{"hashes":["$lost"]}""".encodeToByteArray()
      call.sendResponseHeaders(200, body.size.toLong()); call.responseBody.use { it.write(body) }
    }
    server.createContext("/api/blobs/") { call -> call.requestBody.readBytes(); uploaded += call.requestURI.path.substringAfterLast('/'); call.sendResponseHeaders(204, -1); call.close() }
    server.start()
    try {
      store.setMeta(SyncEngine.KEY_URL, "http://127.0.0.1:${server.address.port}")
      store.setMeta("blobs.mode", SyncEncryption(store, vault).state.value.name)
      store.setMeta(SyncEngine.KEY_RECONCILED, "")
      vault.write(SyncEngine.VAULT_TOKEN, "test-token")
      service.uploadPending()
      assertEquals(listOf(lost), uploaded.toList())
      uploaded.clear(); legacyServer = true
      service.uploadPending()
      assertEquals(setOf(kept, lost), uploaded.toSet(), "an older server without the endpoint gets everything")
    } finally { server.stop(0); driver.close() }
  }

}
