package dev.akkraizen.berill.sdk.protocol

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class LocalizedText(val translations: Map<String, String>) {
  init {
    require(translations.isNotEmpty()) { "At least one translation is required" }
    val codes = translations.keys.map(::normalizeCode)
    require(codes.distinct().size == codes.size) { "Duplicate language codes" }
  }

  constructor(vararg translations: Pair<String, String>) : this(mapOf(*translations))

  fun get(language: String, fallbackLanguage: String = "en"): String {
    val normalized = translations.mapKeys { normalizeCode(it.key) }
    val candidates =
      candidates(normalizeCode(language)) + candidates(normalizeCode(fallbackLanguage))
    val code = candidates.firstOrNull { it in normalized } ?: normalized.keys.first()
    return normalized.getValue(code)
  }
}

private fun normalizeCode(code: String): String {
  val normalized = code.trim().replace('_', '-').lowercase()
  require(Regex("[a-z]{2,8}(?:-[a-z0-9]{1,8})*").matches(normalized) && normalized != "system") {
    "Invalid language code: $code"
  }
  return normalized
}

private fun candidates(code: String): List<String> =
  generateSequence(code) { it.substringBeforeLast('-', "").ifEmpty { null } }.toList()
