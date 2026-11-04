package dev.akkraizen.berill.core.notes

import dev.akkraizen.berill.api.DataAccess
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.ui.markdown.markdownPlainText
import dev.akkraizen.berill.core.ui.CoreText
import dev.akkraizen.berill.protocol.Collections
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class Note(val title: String = "", val body: String = "", val updatedAt: Long = 0, val pinned: Boolean = false, val color: String? = null)

fun Note.displayTitle(): String = title.trim().ifEmpty { plainLines().firstOrNull()?.take(80) ?: CoreText.untitled() }

fun Note.preview(): String {
  val lines = plainLines()
  val rest = if (title.isBlank()) lines.drop(1) else lines
  return rest.joinToString(" ").take(160)
}

fun Note.tags(): List<String> = noteTags(body)

fun noteTags(body: String): List<String> {
  val withoutCode = body.replace(Regex("(?s)```.*?```|~~~.*?~~~|`[^`]*`"), "")
  return Regex("(?<![\\p{L}\\p{N}_&#/\\]])#([\\p{L}\\p{N}_][\\p{L}\\p{N}_/-]*)").findAll(withoutCode)
    .map { it.groupValues[1].trimEnd('/', '-').lowercase() }
    .filter { tag -> tag.isNotEmpty() && !tag.all(Char::isDigit) }
    .distinct().toList()
}

val NOTE_COLORS = listOf("red", "orange", "yellow", "green", "teal", "blue", "purple", "pink")

private fun Note.plainLines(): List<String> = markdownPlainText(body).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()

class NotesRepository(data: DataAccess, private val now: () -> Long) {
  private val collection = data.collection(Collections.NOTES, Note.serializer())

  fun observe(): Flow<List<Item<Note>>> = collection.observe().map { list -> list.sortedByDescending { it.value.updatedAt } }

  fun get(id: String): Note? = collection.get(id)

  fun observeNote(id: String): Flow<Note?> = collection.observe(id)

  fun all(): List<Item<Note>> = collection.all().sortedByDescending { it.value.updatedAt }

  fun backlinks(id: String, title: String): Flow<List<Item<Note>>> = observe().map { notes ->
    notes.filter { it.id != id && wikiTargets(it.value.body).any { target -> target.equals(title.trim(), ignoreCase = true) } }
  }

  fun findByTitle(title: String): String? {
    val wanted = title.trim()
    return collection
      .all()
      .firstOrNull { it.value.displayTitle().equals(wanted, ignoreCase = true) }
      ?.id
  }

  fun save(id: String, title: String, body: String): Boolean {
    val current = collection.get(id)
    if (current == null && title.isBlank() && body.isBlank()) return false
    if (current != null && current.title == title && current.body == body) return false
    collection.save(id, (current ?: Note()).copy(title = title, body = body, updatedAt = now()))
    return true
  }

  fun setPinned(id: String, pinned: Boolean) {
    val current = collection.get(id) ?: return
    if (current.pinned != pinned) collection.save(id, current.copy(pinned = pinned))
  }

  fun setColor(id: String, color: String?) {
    val current = collection.get(id) ?: return
    val next = color?.takeIf { it in NOTE_COLORS }
    if (current.color != next) collection.save(id, current.copy(color = next))
  }

  fun tags(): Flow<List<Pair<String, Int>>> = observe().map { notes ->
    notes.flatMap { it.value.tags() }.groupingBy { it }.eachCount().entries
      .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }
  }

  fun delete(id: String) = collection.delete(id)
}

internal fun wikiTargets(body: String): List<String> {
  val withoutCode = body.replace(Regex("(?s)```.*?```|~~~.*?~~~|`[^`]*`"), "")
  return Regex("\\[\\[([^]\\n]+)]]").findAll(withoutCode).map {
    it.groupValues[1].substringBefore('|').substringBefore('#').trim()
  }.filter { it.isNotEmpty() }.toList()
}

internal class RepositoryNoteDirectory(private val notes: NotesRepository, private val newId: () -> String) : dev.akkraizen.berill.api.NoteDirectory {
  override val available = true

  private fun Item<Note>.ref() = dev.akkraizen.berill.api.NoteRef(id, value.displayTitle(), value.preview(), value.updatedAt)

  override fun observe(): Flow<List<dev.akkraizen.berill.api.NoteRef>> = notes.observe().map { list -> list.map { it.ref() } }

  override fun get(id: String): dev.akkraizen.berill.api.NoteRef? = notes.get(id)?.let { Item(id, it).ref() }

  override fun create(title: String, body: String): String? {
    val id = newId()
    return id.takeIf { notes.save(it, title, body) }
  }
}
