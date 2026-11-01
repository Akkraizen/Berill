package dev.akkraizen.berill.plugins.books.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import dev.akkraizen.berill.plugins.books.i18n.pluginI18n
import kotlinx.serialization.Serializable

@Serializable(with = BookStatusSerializer::class)
enum class BookStatus { NOT_STARTED, READING, DONE }
object BookStatusSerializer : LegacyEnumSerializer<BookStatus>("books.status", BookStatus.entries,
  { pluginI18n.tr("legacy.BookStatus.${it.name}").get("ru") }, BookStatus.NOT_STARTED)
