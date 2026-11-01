package dev.akkraizen.berill.core.preferences

import dev.akkraizen.berill.core.data.LocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

@Serializable
data class HomePreferences(val hiddenCards: Set<String> = emptySet(), val cardOrder: List<String> = emptyList(), val hiddenActions: Set<String> = emptySet(), val actionOrder: List<String> = emptyList(), val greeting: Boolean = true)
@Serializable
data class EditorPreferences(val lineNumbers: Boolean = false, val fontSize: Int = 16, val monospace: Boolean = false)

class LocalPreferences<T>(private val store: LocalStore, private val key: String, private val serializer: KSerializer<T>, val defaults: T) {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val mutable = MutableStateFlow(try { store.meta(key)?.let { json.decodeFromString(serializer, it) } ?: defaults } catch (e: Exception) { defaults })
  val state = mutable.asStateFlow()
  fun set(value: T) { store.setMeta(key, json.encodeToString(serializer, value)); mutable.value = value }
  fun reset() = set(defaults)
}

fun orderedKeys(available: List<String>, preferred: List<String>): List<String> =
  (preferred.filter { it in available } + available).distinct()

fun moveKey(order: List<String>, id: String, delta: Int): List<String> {
  val from = order.indexOf(id)
  val to = from + delta
  if (from < 0 || to !in order.indices) return order
  return order.toMutableList().apply { removeAt(from); add(to, id) }
}

fun mergeOrder(previous: List<String>, visibleOrder: List<String>): List<String> {
  val iterator = visibleOrder.iterator()
  val retained = previous.map { if (it in visibleOrder && iterator.hasNext()) iterator.next() else it }
  return (retained + iterator.asSequence().toList()).distinct()
}
