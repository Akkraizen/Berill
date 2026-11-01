package dev.akkraizen.berill.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Full-text index of all records (SQLite FTS4, available in Android's SQLite and in sqlite-jdbc).
 * The table lives outside the SQLDelight schema so that existing databases get it without a
 * migration. Text is lower-cased here because FTS4's portable tokenizer only folds ASCII; each
 * search term matches words that start with it.
 */
class SearchIndex(private val driver: SqlDriver) {
  init {
    driver.execute(null, "CREATE VIRTUAL TABLE IF NOT EXISTS record_fts USING fts4(collection, record_id, title, content, notindexed=collection, notindexed=record_id)", 0)
  }

  /** Called by [LocalStore] inside its write transaction. */
  fun update(record: SyncRecord) {
    val docId = docId(record.collection, record.id)
    driver.execute(null, "DELETE FROM record_fts WHERE docid = ?", 1) { bindLong(0, docId) }
    if (record.deleted) return
    val title = TITLE_FIELDS.firstNotNullOfOrNull { (record.fields[it] as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()
    val content = buildString { collectText(record.fields, this) }
    if (title.isBlank() && content.isBlank()) return
    driver.execute(null, "INSERT INTO record_fts(docid, collection, record_id, title, content) VALUES (?, ?, ?, ?, ?)", 5) {
      bindLong(0, docId)
      bindString(1, record.collection)
      bindString(2, record.id)
      bindString(3, normalize(title))
      bindString(4, normalize(content.take(MAX_CONTENT)))
    }
  }

  fun clear() {
    driver.execute(null, "DELETE FROM record_fts", 0)
  }

  /**
   * Records whose words start with every term, best first: all terms in the title, then some, then
   * only in the text. Returns (collection, id) pairs.
   */
  fun search(query: String, collections: Set<String>?, limit: Int): List<Pair<String, String>> {
    val terms = terms(query)
    if (terms.isEmpty()) return emptyList()
    val match = terms.joinToString(" ") { "$it*" }
    val hits = driver.executeQuery(null, "SELECT collection, record_id, title FROM record_fts WHERE record_fts MATCH ? LIMIT ?", { cursor ->
      val rows = mutableListOf<Triple<String, String, String>>()
      while (cursor.next().value) rows += Triple(cursor.getString(0)!!, cursor.getString(1)!!, cursor.getString(2).orEmpty())
      QueryResult.Value(rows)
    }, 2) {
      bindString(0, match)
      bindLong(1, (limit * 4L).coerceAtMost(2000))
    }.value
    return hits.asSequence()
      .filter { collections == null || it.first in collections }
      .sortedBy { (_, _, title) ->
        val words = title.split(SEPARATORS)
        val inTitle = terms.count { term -> words.any { it.startsWith(term) } }
        when (inTitle) { terms.size -> 0; 0 -> 2; else -> 1 }
      }
      .take(limit)
      .map { it.first to it.second }
      .toList()
  }

  companion object {
    private const val MAX_CONTENT = 200_000
    private val TITLE_FIELDS = listOf("title", "name", "label", "word", "text")
    /** Fields with file contents or encoded images are not text. */
    private val SKIPPED = setOf("data", "image", "cover", "thumbnail")
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    /** Lower case with every non-letter, non-digit as a space, so FTS4 words match [terms] exactly. */
    private fun normalize(text: String): String = text.lowercase().replace(SEPARATORS, " ")

    /** Search terms as FTS4 tokens: letters and digits only, lower case. */
    fun terms(query: String): List<String> = query.lowercase().split(SEPARATORS).filter { it.isNotEmpty() }.take(16)

    private fun collectText(element: JsonElement, out: StringBuilder, key: String? = null) {
      when (element) {
        is JsonObject -> element.forEach { (k, v) -> if (k !in SKIPPED && !k.startsWith("__")) collectText(v, out, k) }
        is JsonArray -> element.forEach { collectText(it, out, key) }
        is JsonPrimitive -> if (element.isString && !element.content.startsWith("blob:") && element.content.length < 100_000) out.append(element.content).append('\n')
      }
    }

    /** Stable 63-bit id of a record (FNV-1a), so updates replace the old entry without a lookup. */
    fun docId(collection: String, id: String): Long {
      var hash = -0x340d631b7bdddcdbL
      for (byte in "$collection\u0000$id".encodeToByteArray()) {
        hash = hash xor (byte.toLong() and 0xff)
        hash *= 0x100000001b3L
      }
      return hash and Long.MAX_VALUE
    }
  }
}
