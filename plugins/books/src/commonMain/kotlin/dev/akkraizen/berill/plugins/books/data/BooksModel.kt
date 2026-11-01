package dev.akkraizen.berill.plugins.books.data

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.books.BooksPlugin
import kotlinx.serialization.Serializable

val NOT_STARTED = BookStatus.NOT_STARTED
val READING = BookStatus.READING
val DONE = BookStatus.DONE
val STATUSES = listOf(NOT_STARTED, READING, DONE)

@Serializable
data class Book(
  val title: String = "",
  val author: String? = null,
  val status: BookStatus = NOT_STARTED,
  val genre: String? = null,
  val pages: Int? = null,
  val rating: Int? = null,
  val started: String? = null,
  val finished: String? = null,
  val cover: String? = null,
  val body: String? = null
) {

  val normalizedStatus: BookStatus
    get() = if (status in STATUSES) status else NOT_STARTED

  fun moveTo(target: BookStatus): Book {
    val now = today().toString()
    return when (target) {
      READING -> copy(status = target, started = started ?: now, finished = null)
      DONE -> copy(status = target, started = started ?: now, finished = finished ?: now)
      else -> copy(status = target, started = null, finished = null)
    }
  }
}

internal fun books(ctx: PluginContext) = ctx.data.collection(BooksPlugin.BOOKS, Book.serializer())
