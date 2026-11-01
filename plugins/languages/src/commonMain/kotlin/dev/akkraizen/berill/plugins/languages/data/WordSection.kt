package dev.akkraizen.berill.plugins.languages.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import kotlinx.serialization.Serializable
@Serializable(with = WordSectionSerializer::class)
enum class WordSection { WORDS, PHRASES }
object WordSectionSerializer : LegacyEnumSerializer<WordSection>("languages.section", WordSection.entries,
 { LegacyValues.wordSection.getValue(it) }, WordSection.WORDS)
