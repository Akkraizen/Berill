package dev.akkraizen.berill.plugins.languages.data

/**
 * Values stored in synced records by older versions (and imported from Obsidian). They are data,
 * not interface text: never translate or edit them, or existing records stop matching.
 * This file is exempt from the hard-coded Cyrillic check (verifySourceLocalization).
 */
internal object LegacyValues {
  val wordSection: Map<WordSection, String> = mapOf(
    WordSection.WORDS to "Слова",
    WordSection.PHRASES to "Фразы",
  )
}
