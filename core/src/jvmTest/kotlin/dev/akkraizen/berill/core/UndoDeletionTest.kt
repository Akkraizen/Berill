package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.protocol.HybridClock
import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UndoDeletionTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private var time = 100L
  private val clock = HybridClock("local") { time++ }
  private val store: LocalStore
  init {
    BerillDatabase.Schema.create(driver)
    store = LocalStore(BerillDatabase(driver), clock)
  }
  @AfterTest fun close() { driver.close() }

  @Test
  fun restoresParentAndAssociatedRecordsAndSyncsResurrection() {
    store.put("recipes", "recipe", mapOf("title" to JsonPrimitive("Soup")))
    store.put("images", "photo", mapOf("recipe" to JsonPrimitive("recipe"), "data" to JsonPrimitive("image")))
    store.delete("images", "photo")
    store.delete("recipes", "recipe")
    val batch = store.pendingDeletions.value
    val tombstone = store.get("recipes", "recipe")!!
    assertEquals(2, batch.size)
    assertTrue(store.list("recipes").isEmpty())
    store.undoDeletions(batch)
    assertEquals("Soup", (store.get("recipes", "recipe")!!.fields["title"] as JsonPrimitive).content)
    assertEquals(1, store.list("images").size)
    assertTrue(store.pendingDeletions.value.isEmpty())
    val restored = store.get("recipes", "recipe")!!
    assertFalse(restored.deleted)
    assertFalse(tombstone.mergeWith(restored).deleted)
    assertTrue(store.dirty().any { it.first.id == "recipe" && !it.first.deleted })
  }

  @Test
  fun doesNotUndoLaterRemoteDeletion() {
    store.put("items", "item", mapOf("name" to JsonPrimitive("First")))
    store.delete("items", "item")
    val batch = store.pendingDeletions.value
    val remote = HybridClock("remote") { 10000L }
    store.applyRemote(listOf(SyncRecord("items", "item", deletedAt = remote.tick())))
    store.undoDeletions(batch)
    assertTrue(store.get("items", "item")!!.deleted)
  }

  @Test
  fun keepsNewerEditsAndDismissesOnlyTheDisplayedBatch() {
    store.put("items", "first", mapOf("name" to JsonPrimitive("Before")))
    store.delete("items", "first")
    val batch = store.pendingDeletions.value
    store.put("items", "first", mapOf("name" to JsonPrimitive("After")))
    store.put("items", "second", mapOf("name" to JsonPrimitive("Second")))
    store.delete("items", "second")
    store.undoDeletions(batch)
    assertEquals(JsonPrimitive("After"), store.get("items", "first")!!.fields["name"])
    assertEquals(listOf("second"), store.pendingDeletions.value.map { it.id })
    store.dismissDeletions(store.pendingDeletions.value)
    assertTrue(store.get("items", "second")!!.deleted)
  }

  @Test
  fun missingOrAlreadyDeletedRecordsDoNotCreateExtraUndoEntries() {
    store.delete("items", "missing")
    store.put("items", "item", mapOf("name" to JsonPrimitive("One")))
    store.delete("items", "item")
    store.delete("items", "item")
    assertEquals(1, store.pendingDeletions.value.size)
  }
}
