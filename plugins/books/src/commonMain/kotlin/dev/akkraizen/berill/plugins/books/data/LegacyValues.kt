package dev.akkraizen.berill.plugins.books.data

/**
 * Values stored in synced records by older versions (and imported from Obsidian). They are data,
 * not interface text: never translate or edit them, or existing records stop matching.
 * This file is exempt from the hard-coded Cyrillic check (verifySourceLocalization).
 */
internal object LegacyValues {
  val bookStatus: Map<BookStatus, String> = mapOf(
    BookStatus.NOT_STARTED to "не начато",
    BookStatus.READING to "в процессе чтения",
    BookStatus.DONE to "прочитано",
  )
}
