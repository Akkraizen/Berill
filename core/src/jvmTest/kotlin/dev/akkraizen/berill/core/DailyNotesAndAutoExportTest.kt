package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.FileAccess
import dev.akkraizen.berill.api.PickedFile
import dev.akkraizen.berill.core.attachments.Attachments
import dev.akkraizen.berill.core.attachments.MemoryAttachmentCache
import dev.akkraizen.berill.core.connections.*
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.data.StoreDataAccess
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.notes.DailyNotes
import dev.akkraizen.berill.core.notes.NotesRepository
import dev.akkraizen.berill.core.secure.MemoryVault
import dev.akkraizen.berill.core.sync.SyncEncryption
import dev.akkraizen.berill.protocol.HybridClock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.*

class DailyNotesAndAutoExportTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val store: LocalStore
  init { BerillDatabase.Schema.create(driver); store = LocalStore(BerillDatabase(driver), HybridClock("daily") { 1L }) }
  @AfterTest fun close() { driver.close() }

  @Test fun dailyNoteUsesTheTemplateOnceAndKeepsMoodOnTheFirstLine() {
    val notes = NotesRepository(StoreDataAccess(store)) { 1L }
    val daily = DailyNotes(notes, store)
    val date = LocalDate(2026, 11, 2)
    daily.setTemplate("## {{weekday}}, {{date}}\n\n- [ ] ")
    assertEquals("## Mon, 02.11.2026\n\n- [ ] ", DailyNotes.render(daily.template(), date, listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")))
    val id = daily.ensure(date)
    assertEquals("journal-2026-11-02", id)
    assertTrue(notes.get(id)!!.body.contains("02.11.2026"))
    notes.save(id, notes.get(id)!!.title, "my own text")
    daily.ensure(date)
    assertEquals("my own text", notes.get(id)!!.body, "an existing note is never replaced")
    daily.setMood(date, 3)
    assertEquals(3, daily.mood(date))
    assertTrue(notes.get(id)!!.body.endsWith("my own text"))
    daily.setMood(date, 0)
    assertEquals(0, daily.mood(date))
    assertEquals(1, notes.get(id)!!.body.lines().count { line -> line.isNotBlank() && !line.contains("my own text") })
  }

  private class FolderFiles : FileAccess {
    val files = linkedMapOf<String, ByteArray>()
    var fail = false
    override suspend fun pickImage(maxSide: Int): PickedFile? = null
    override suspend fun writeToFolder(folder: String, name: String, bytes: ByteArray): Boolean { if (fail) return false; files[name] = bytes; return true }
    override suspend fun listFolder(folder: String): List<String> = files.keys.toList() + "unrelated.zip"
    override suspend fun deleteFromFolder(folder: String, name: String): Boolean = files.remove(name) != null
  }

  @Test fun automaticExportRunsWhenDueKeepsTheNewestAndRecordsFailures() = runBlocking {
    val vault = MemoryVault()
    val archive = LocalArchiveService(store, Attachments(MemoryAttachmentCache(), store, vault, SyncEncryption(store, vault)))
    val folder = FolderFiles()
    var now = 1_800_000_000_000L
    val export = AutoExport(store, archive, folder) { now }
    assertFalse(export.isDue(), "off by default")
    export.preferences.set(AutoExportSettings(enabled = true, folder = "/backups", intervalDays = 1, keep = 2))
    store.put("notes", "n", mapOf("title" to kotlinx.serialization.json.JsonPrimitive("x")))
    repeat(3) {
      assertTrue(export.runIfDue())
      assertFalse(export.runIfDue(), "not due again within the interval")
      now += 25L * 60 * 60 * 1000
    }
    assertEquals(2, folder.files.size)
    assertTrue(folder.files.keys.all { it.startsWith("berill-auto-") })
    assertEquals(listOf("notes"), decodeLocalArchive(folder.files.values.last()).map { it.collection })
    folder.fail = true
    assertFalse(export.runNow())
    assertEquals(FailureReason.ACCESS_DENIED, export.status.value!!.failure)
    assertTrue(export.isDue(), "a failed run is retried")
  }
}
