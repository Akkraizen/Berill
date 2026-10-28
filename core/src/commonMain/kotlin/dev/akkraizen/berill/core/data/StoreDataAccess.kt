package dev.akkraizen.berill.core.data

import dev.akkraizen.berill.api.DataAccess
import dev.akkraizen.berill.api.DataCollection
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.RawCollection
import dev.akkraizen.berill.api.RawRecord
import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class StoreDataAccess(private val store: LocalStore) : DataAccess {
  override fun <T> collection(
    name: String,
    serializer: KSerializer<T>
  ): DataCollection<T> = TypedCollection(store, name, serializer)

  override fun raw(name: String): RawCollection = StoreRawCollection(store, name)

  @OptIn(ExperimentalUuidApi::class)
  override fun newId(): String = Uuid.random().toString()
}

class TypedCollection<T>(
  private val store: LocalStore,
  override val name: String,
  private val serializer: KSerializer<T>
) : DataCollection<T> {
  override fun observe(): Flow<List<Item<T>>> = store.observe(name).map { records -> records.mapNotNull(::decode) }

  override fun observe(id: String): Flow<T?> =
    store
      .observe(name)
      .map { records -> records.firstOrNull { it.id == id }?.let(::decode)?.value }
      .distinctUntilChanged()

  override fun get(id: String): T? = store.get(name, id)?.takeUnless { it.deleted }?.let(::decode)?.value

  override fun all(): List<Item<T>> = store.list(name).mapNotNull(::decode)

  override fun save(id: String, value: T) {
    store.put(name, id, json.encodeToJsonElement(serializer, value).jsonObject)
  }

  override fun delete(id: String) = store.delete(name, id)

  private fun decode(record: SyncRecord): Item<T>? =
    runCatching { Item(record.id, json.decodeFromJsonElement(serializer, record.fields)) }
      .getOrNull()

  private companion object {

    val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      isLenient = true
      coerceInputValues = true
    }
  }
}

class StoreRawCollection(private val store: LocalStore, override val name: String) : RawCollection {
  override fun observe(): Flow<List<RawRecord>> = store.observe(name).map { records -> records.map { RawRecord(it.id, it.fields) } }

  override fun get(id: String): RawRecord? = store.get(name, id)?.takeUnless { it.deleted }?.let { RawRecord(it.id, it.fields) }

  override fun all(): List<RawRecord> = store.list(name).filterNot { it.deleted }.map { RawRecord(it.id, it.fields) }

  override fun patch(id: String, fields: Map<String, JsonElement>) = store.put(name, id, fields)

  override fun delete(id: String) = store.delete(name, id)
}
