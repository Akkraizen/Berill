package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.TodayItem
import dev.akkraizen.berill.api.TodayKind
import dev.akkraizen.berill.api.ui.layout.applyManualOrder
import dev.akkraizen.berill.api.ui.layout.mergeManualOrder
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.data.StoreDataAccess
import dev.akkraizen.berill.core.data.TRASH_KEEP_MILLIS
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.notes.NotesRepository
import dev.akkraizen.berill.core.notes.noteTags
import dev.akkraizen.berill.core.today.sortToday
import dev.akkraizen.berill.protocol.HybridClock
import kotlin.test.*

class NotesOrganizationTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private var now = 1_000L
  private val store: LocalStore
  init { BerillDatabase.Schema.create(driver); store = LocalStore(BerillDatabase(driver), HybridClock("notes") { now }) }
  @AfterTest fun close() { driver.close() }

  @Test fun tagsIgnoreHeadingsCodeAndLinks() {
    val body = "# Heading\n#work and #учёба/сети, not a#b, `#code`, [[Page#part]], #2026, #Work again\n```\n#hidden\n```"
    assertEquals(listOf("work", "учёба/сети"), noteTags(body))
  }

  @Test fun pinAndColorSurviveEditsAndUnknownColorsAreDropped() {
    val notes = NotesRepository(StoreDataAccess(store)) { now }
    notes.save("n", "Title", "Body")
    notes.setPinned("n", true)
    notes.setColor("n", "green")
    notes.save("n", "Title", "Changed")
    val saved = notes.get("n")!!
    assertTrue(saved.pinned)
    assertEquals("green", saved.color)
    notes.setColor("n", "magenta")
    assertNull(notes.get("n")!!.color)
  }

  @Test fun manualOrderKeepsHiddenItemsInPlace() {
    assertEquals(listOf("c", "a", "b"), applyManualOrder(listOf("a", "b", "c"), listOf("c", "a")) { it })
    assertEquals(listOf("a", "x", "c", "b"), mergeManualOrder(listOf("a", "x", "b", "c"), listOf("a", "c", "b")))
    assertEquals(listOf("b", "a", "z"), mergeManualOrder(listOf("z"), listOf("b", "a")))
  }

  @Test fun trashIsPurgedAfterThirtyDays() {
    store.put("notes", "old", mapOf("title" to kotlinx.serialization.json.JsonPrimitive("Old")))
    store.delete("notes", "old")
    now += 10L * 24 * 60 * 60 * 1000
    store.put("notes", "fresh", mapOf("title" to kotlinx.serialization.json.JsonPrimitive("Fresh")))
    store.delete("notes", "fresh")
    assertEquals(0, store.purgeExpired(now))
    assertEquals(2, store.deletedRecords().size)
    now = 1_000L + TRASH_KEEP_MILLIS + 1
    assertEquals(1, store.purgeExpired(now))
    assertEquals(listOf("fresh"), store.deletedRecords().map { it.id })
  }

  @Test fun todayPutsOverdueAndTimedItemsFirstAndDoneLast() {
    fun item(title: String, time: String? = null, done: Boolean? = null, overdue: Boolean = false) =
      TodayItem(title, title, TodayKind.TASK, "p", time = time, done = done, overdue = overdue) {}
    val sorted = sortToday(listOf(item("done", done = true), item("plain"), item("late", "18:00"), item("early", "08:00"), item("overdue", overdue = true)))
    assertEquals(listOf("overdue", "early", "late", "plain", "done"), sorted.map { it.title })
  }
}
