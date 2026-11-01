package dev.akkraizen.berill.api.ui.state

import androidx.compose.runtime.*
import dev.akkraizen.berill.api.DataCollection
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Device-local drafts and templates; never written into synced user collections. */
enum class QuickEntryKind { NOTE, OPERATION, FOOD, FOOD_MEAL, WORKOUT, TASK }

class QuickEntries(private val read: (String) -> String?, private val write: (String, String) -> Unit, private val performUndo: (() -> Boolean) -> Boolean = { it() }, val onError: (Exception) -> Unit) {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  var revision by mutableStateOf(0); private set
  var saved by mutableStateOf<QuickSaved?>(null); private set
  private var sequence = 0L
  private fun key(kind: QuickEntryKind, templates: Boolean) = "quick.${kind.name.lowercase()}.${if (templates) "templates" else "draft"}"
  fun <T> load(kind: QuickEntryKind, serializer: KSerializer<T>, initial: T): T = safely(initial) {
    read(key(kind, false))?.takeIf { it.isNotBlank() }?.let { json.decodeFromString(serializer, it) } ?: initial
  }
  fun <T> store(kind: QuickEntryKind, serializer: KSerializer<T>, value: T) {
    safely(Unit) { write(key(kind, false), json.encodeToString(serializer, value)) }
  }
  fun clear(kind: QuickEntryKind) { safely(Unit) { write(key(kind, false), "") } }
  fun <T> templates(kind: QuickEntryKind, serializer: KSerializer<T>): List<T> = safely(emptyList()) {
    read(key(kind, true))?.takeIf { it.isNotBlank() }?.let { json.decodeFromString(ListSerializer(serializer), it) } ?: emptyList()
  }
  fun <T> addTemplate(kind: QuickEntryKind, serializer: KSerializer<T>, value: T) {
    setTemplates(kind, serializer, (templates(kind, serializer) + value).distinct())
  }
  fun <T> removeTemplate(kind: QuickEntryKind, serializer: KSerializer<T>, value: T) {
    setTemplates(kind, serializer, templates(kind, serializer).filterNot { it == value })
  }
  private fun <T> setTemplates(kind: QuickEntryKind, serializer: KSerializer<T>, values: List<T>) {
    safely(Unit) { write(key(kind, true), json.encodeToString(ListSerializer(serializer), values)); revision++ }
  }
  fun created(undo: () -> Boolean) { saved = QuickSaved(++sequence, undo) }
  fun dismiss(token: QuickSaved) { if (saved === token) saved = null }
  fun undo(token: QuickSaved): Boolean {
    if (saved !== token) return false
    return performUndo(token.undo).also { if (it) dismiss(token) }
  }
  private inline fun <T> safely(fallback: T, block: () -> T): T = try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { onError(e); fallback }
}
class QuickSaved internal constructor(val id: Long, internal val undo: () -> Boolean)

val LocalQuickEntries = staticCompositionLocalOf<QuickEntries> { error("QuickEntries provider is missing") }

class QuickDraft<T>(private val entries: QuickEntries, private val kind: QuickEntryKind, private val serializer: KSerializer<T>, initial: T) : MutableState<T> {
  private var current by mutableStateOf(entries.load(kind, serializer, initial))
  override var value: T
    get() = current
    set(value) { current = value; entries.store(kind, serializer, value) }
  override fun component1(): T = value
  override fun component2(): (T) -> Unit = { value = it }
  fun reset(value: T) { current = value; entries.clear(kind) }
}

@Composable
fun <T> rememberQuickDraft(kind: QuickEntryKind, serializer: KSerializer<T>, initial: T): QuickDraft<T> {
  val entries = LocalQuickEntries.current
  return remember(entries, kind) { QuickDraft(entries, kind, serializer, initial) }
}

/** Never undo an entry that has since been edited locally or on another device. */
fun <T> saveQuickEntry(entries: QuickEntries, collection: DataCollection<T>, id: String, value: T) {
  collection.save(id, value)
  entries.created {
    if (collection.get(id) == value) { collection.delete(id); true } else false
  }
}

/** A repeated meal is one reversible action, including all its foods. */
fun <T> saveQuickBatch(entries: QuickEntries, collection: DataCollection<T>, values: List<Pair<String, T>>) {
  require(values.map { it.first }.distinct().size == values.size)
  require(values.all { collection.get(it.first) == null })
  if (values.isEmpty()) return
  try {
    values.forEach { (id, value) -> collection.save(id, value) }
  } catch (e: Exception) {
    values.forEach { (id, value) ->
      try { if (collection.get(id) == value) collection.delete(id) }
      catch (cleanup: Exception) { e.addSuppressed(cleanup) }
    }
    throw e
  }
  entries.created {
    if (values.all { (id, value) -> collection.get(id).let { it == null || it == value } }) {
      values.forEach { (id, value) -> if (collection.get(id) == value) collection.delete(id) }; true
    } else false
  }
}
