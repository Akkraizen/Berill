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
  val name: String

  fun observe(): Flow<List<RawRecord>>

  fun get(id: String): RawRecord?

  fun all(): List<RawRecord>

  fun patch(id: String, fields: Map<String, JsonElement>)

  fun delete(id: String)
}

data class RawRecord(val id: String, val fields: JsonObject)
