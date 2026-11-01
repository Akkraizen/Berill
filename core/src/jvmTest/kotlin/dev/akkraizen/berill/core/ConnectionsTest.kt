package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.core.connections.*
import dev.akkraizen.berill.core.attachments.*
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.notes.wikiTargets
import dev.akkraizen.berill.core.secure.MemoryVault
import dev.akkraizen.berill.core.sync.SyncEncryption
import dev.akkraizen.berill.protocol.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class ConnectionsTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val store: LocalStore
  init { BerillDatabase.Schema.create(driver); store = LocalStore(BerillDatabase(driver), HybridClock("local") { 1L }) }
  @AfterTest fun close() { driver.close() }

  @Test fun attachmentMigrationDoesNotOverwriteOrderingOrResurrectDeletedPhotos() {
    val old = JsonPrimitive("legacy-base64")
    val replacement = JsonPrimitive("blob:" + "a".repeat(64))
    store.put("photos", "photo", mapOf("data" to old, "position" to JsonPrimitive(0)))
    store.put("photos", "photo", mapOf("position" to JsonPrimitive(2)))
    val restarted = LocalStore(BerillDatabase(driver), HybridClock("restarted") { 0L })
    assertTrue(restarted.putIfCurrent("photos", "photo", "data", old, replacement))
    assertEquals(JsonPrimitive(2), store.get("photos", "photo")!!.fields["position"])
    assertFalse(store.putIfCurrent("photos", "photo", "data", old, replacement))
    restarted.delete("photos", "photo")
    assertFalse(store.putIfCurrent("photos", "photo", "data", replacement, old))
    assertTrue(store.get("photos", "photo")!!.deleted)
  }

  @Test fun backlinksIgnoreCodeAndResolveAliasesAndHeadings() {
    assertEquals(listOf("Recipe", "Recipe"), wikiTargets("[[Recipe|Dinner]] [[Recipe#Ingredients]] `[[Ignore]]`\n```\n[[Ignore too]]\n```"))
  }
  @Test fun restoringObservedFutureTombstoneWinsWithoutChangingOtherRecords() {
    val stamp = HybridClock.encode(999999, 1, "remote")
    store.applyRemote(listOf(SyncRecord("notes", "deleted", JsonObject(mapOf("title" to JsonPrimitive("Old"))), mapOf("title" to HybridClock.encode(1,0,"remote")), stamp)))
    store.restore("notes", "deleted")
    assertFalse(store.get("notes", "deleted")!!.deleted)
    assertTrue(store.get("notes", "deleted")!!.latestClock!! > stamp)
    assertEquals(1, store.dirty().size)
  }
  @Test fun localArchiveKeepsClocksTombstonesAndBinaryFiles() {
    val bytes = byteArrayOf(0, 1, 2, 127, -1)
    val hash = attachmentHash(bytes)
    val records = listOf(SyncRecord("notes", "../unsafe", JsonObject(mapOf("body" to JsonPrimitive("[[Link]]"))), mapOf("body" to "stamp"), "deleted"))
    val archive = encodeLocalArchive(records, mapOf(hash to bytes))
    assertEquals(records, decodeLocalArchive(archive))
    assertContentEquals(bytes, decodeArchiveAttachments(archive)[hash])
  }
  @Test fun traversalAndWrongBlobChecksumAreRejectedBeforeImport() {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip -> zip.putNextEntry(ZipEntry("../records.json")); zip.write("[]".encodeToByteArray()); zip.closeEntry() }
    assertFailsWith<IllegalArgumentException> { decodeLocalArchive(out.toByteArray()) }
    assertFailsWith<IllegalArgumentException> { encodeLocalArchive(emptyList(), mapOf("0".repeat(64) to byteArrayOf(1))) }
  }
  @Test fun offlineAttachmentsAreDeduplicatedAndEncryptedOnTransfer() = runBlocking {
    val vault = MemoryVault()
    val encryption = SyncEncryption(store, vault)
    val service = Attachments(MemoryAttachmentCache(), store, vault, encryption)
    val bytes = "private photo".encodeToByteArray()
    val first = service.put(bytes)
    assertEquals(first, service.put(bytes))
    assertEquals(1, service.cache.hashes().size)
    encryption.enable("password123")
    val encrypted = encryption.encryptAttachment(bytes)
    assertFalse(encrypted.contentEquals(bytes))
    assertContentEquals(bytes, encryption.decryptAttachment(encrypted))
    encryption.forgetKey()
    assertNull(encryption.decryptAttachment(encrypted))
    assertFailsWith<IllegalStateException> { encryption.encryptAttachment(bytes) }
    Unit
  }
}
