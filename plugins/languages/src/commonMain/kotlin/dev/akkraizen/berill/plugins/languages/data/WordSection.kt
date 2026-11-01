package dev.akkraizen.berill.plugins.languages.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import dev.akkraizen.berill.plugins.languages.i18n.pluginI18n
import kotlinx.serialization.Serializable
@Serializable(with = WordSectionSerializer::class)
enum class WordSection { WORDS, PHRASES }
object WordSectionSerializer : LegacyEnumSerializer<WordSection>("languages.section", WordSection.entries,
 { pluginI18n.tr("legacy.WordSection.${it.name}").get("ru") }, WordSection.WORDS)
