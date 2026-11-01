package dev.akkraizen.berill.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

interface DataAccess {
  fun <T> collection(name: String, serializer: KSerializer<T>): DataCollection<T>

  fun raw(name: String): RawCollection

  fun newId(): String
}

inline fun <reified T> DataAccess.collection(name: String): DataCollection<T> = collection(name, serializer<T>())

interface DataCollection<T> {
  val name: String

  fun observe(): Flow<List<Item<T>>>

  fun observe(id: String): Flow<T?>

  fun get(id: String): T?

  fun all(): List<Item<T>>

  fun save(id: String, value: T)

  fun delete(id: String)
}

data class Item<T>(val id: String, val value: T)

interface RawCollection {
  fun createIfAbsent(id: String, fields: JsonObject): Boolean = false

  fun mutateIfCurrent(id: String, expected: JsonObject, patch: JsonObject?, delete: Boolean = false): Boolean = false

  // Unsupported stores keep the old value; implementations must compare atomically.
  fun patchIfCurrent(id: String, field: String, expected: JsonElement, value: JsonElement): Boolean = false

  fun isDeleted(id: String): Boolean = false

  /**
   * Records whose words start with every word of [query], best matches first, from the full-text
   * index; null if the store has no index, in which case callers scan [all] themselves.
   */
  fun search(query: String, limit: Int = 200): List<RawRecord>? = null

  val name: String

  fun observe(): Flow<List<RawRecord>>

  fun get(id: String): RawRecord?

  fun all(): List<RawRecord>

  fun patch(id: String, fields: Map<String, JsonElement>)

  fun delete(id: String)
}

data class RawRecord(val id: String, val fields: JsonObject)
