package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.data.SearchIndex
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.protocol.HybridClock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class SearchIndexTest {
  private fun text(value: String) = JsonPrimitive(value)

  @Test fun findsWordPrefixesInAnyLanguageAndRanksTitlesFirst() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    try {
      BerillDatabase.Schema.create(driver)
      val store = LocalStore(BerillDatabase(driver), HybridClock("fts") { 1L }, SearchIndex(driver))
      store.put("notes", "body", mapOf("title" to text("Покупки"), "body" to text("Купить книгу «Мастер и Маргарита» — срочно")))
      store.put("notes", "title", mapOf("title" to text("Книги на осень"), "body" to text("список")))
      store.put("books", "b1", mapOf("title" to text("Мастер и Маргарита"), "tags" to JsonArray(listOf(text("классика"))), "cover" to text("книга-обложка")))
      store.put("notes", "photo", mapOf("title" to text("Фото"), "data" to text("blob:" + "a".repeat(64))))

      assertEquals(listOf("title", "body"), store.search("КНИГ", setOf("notes"))!!.map { it.id })
      assertEquals(listOf("b1"), store.search("маст марг", setOf("books"))!!.map { it.id })
      assertEquals(listOf("b1"), store.search("классик")!!.map { it.id }, "arrays are indexed")
      assertEquals(listOf("body"), store.search("срочно")!!.map { it.id }, "punctuation does not glue words")
      assertTrue(store.search("обложка")!!.isEmpty(), "image fields are not text")
      assertTrue(store.search("aaaa")!!.isEmpty(), "attachment references are not text")
      assertTrue(store.search("  ")!!.isEmpty())

      store.put("notes", "title", mapOf("title" to text("Фильмы на осень")))
      assertEquals(listOf("body"), store.search("книг", setOf("notes"))!!.map { it.id })
      store.delete("notes", "body")
      assertTrue(store.search("книг", setOf("notes"))!!.isEmpty())

      // A database created before the index gets it built on the next start.
      driver.execute(null, "DROP TABLE record_fts", 0)
      store.setMeta("maintenance.search_index_v1", "")
      driver.execute(null, "DELETE FROM meta WHERE key = 'maintenance.search_index_v1'", 0)
      val restarted = LocalStore(BerillDatabase(driver), HybridClock("fts2") { 2L }, SearchIndex(driver))
      assertEquals(listOf("b1"), restarted.search("маргарита")!!.map { it.id })
      assertNull(LocalStore(BerillDatabase(driver), HybridClock("plain") { 3L }).search("маргарита"))
    } finally { driver.close() }
  }
}
