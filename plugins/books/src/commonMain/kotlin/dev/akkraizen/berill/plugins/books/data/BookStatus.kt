package dev.akkraizen.berill.plugins.books.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = BookStatusSerializer::class)
enum class BookStatus { NOT_STARTED, READING, DONE }
object BookStatusSerializer : LegacyEnumSerializer<BookStatus>("books.status", BookStatus.entries,
  { LegacyValues.bookStatus.getValue(it) }, BookStatus.NOT_STARTED)
