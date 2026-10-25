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
data class Note(val title: String = "", val body: String = "", val updatedAt: Long = 0)

fun Note.displayTitle(): String = title.trim().ifEmpty { plainLines().firstOrNull()?.take(80) ?: CoreText.untitled() }

fun Note.preview(): String {
  val lines = plainLines()
  val rest = if (title.isBlank()) lines.drop(1) else lines
  return rest.joinToString(" ").take(160)
}

private fun Note.plainLines(): List<String> = markdownPlainText(body).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()

class NotesRepository(data: DataAccess, private val now: () -> Long) {
  private val collection = data.collection(Collections.NOTES, Note.serializer())

  fun observe(): Flow<List<Item<Note>>> = collection.observe().map { list -> list.sortedByDescending { it.value.updatedAt } }

  fun get(id: String): Note? = collection.get(id)

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
    collection.save(id, Note(title = title, body = body, updatedAt = now()))
    return true
  }

  fun delete(id: String) = collection.delete(id)
}
