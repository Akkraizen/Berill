package dev.akkraizen.berill.sdk.i18n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class PluralCategory {
  Zero, One, Two, Few, Many, Other
}

object PluralRules {
  private val rules = mutableMapOf<String, (Int) -> PluralCategory>(
    "other" to { PluralCategory.Other },
    "one-other" to { n -> if (kotlin.math.abs(n.toLong()) == 1L) PluralCategory.One else PluralCategory.Other },
    "zero-one-other" to { n -> if (n in 0..1) PluralCategory.One else PluralCategory.Other },
    "russian" to { n ->
      val a = kotlin.math.abs(n.toLong()) % 100
      val b = a % 10
      when {
        a in 11..14 -> PluralCategory.Many
        b == 1L -> PluralCategory.One
        b in 2..4 -> PluralCategory.Few
        else -> PluralCategory.Many
      }
    }
  )

  fun register(id: String, rule: (Int) -> PluralCategory) {
    require(id.isNotBlank() && id !in rules) { "Invalid or duplicate plural rule: $id" }
    rules[id] = rule
  }

  fun category(rule: String, n: Int): PluralCategory = rules.getValue(rule)(n)

  fun contains(rule: String): Boolean = rule in rules
}

data class CatalogLanguage(val code: String, val nativeName: String, val pluralRule: String)

class TranslationCatalog private constructor(
  val languages: List<CatalogLanguage>,
  private val messages: Map<String, Map<String, JsonElement>>,
  val fallbackLanguage: String
) {
  val keys: Set<String>
    get() = messages.keys

  fun text(
    key: String,
    language: String,
    arguments: Map<String, Any?> = emptyMap(),
    count: Int? = null,
    pluralRule: ((String, Int) -> PluralCategory)? = null
  ): String {
    val (code, value) = resolve(key, language) ?: return key
    val template = when (value) {
      is JsonPrimitive -> {
        require(value.isString) { "$key must be a string" }
        value.content
      }

      is JsonObject -> {
        require(count != null) { "$key requires a count" }
        val rule = languages.first { it.code == code }.pluralRule
        val category =
          (pluralRule?.invoke(code, count) ?: PluralRules.category(rule, count)).name.lowercase()
        (value[category] ?: value.getValue("other")).jsonPrimitive.content
      }

      else -> error("$key is a list; use list()")
    }
    val values = if (count == null) arguments else arguments + ("n" to count)
    return PLACEHOLDER.replace(template) { match ->
      when (match.value) {
        "{{" -> "{"
        "}}" -> "}"
        else -> {
          val name = match.groupValues[1]
          require(name in values) { "Missing argument $name for $key" }
          values.getValue(name).toString()
        }
      }
    }
  }

  fun list(key: String, language: String): List<String> {
    val value = resolve(key, language)?.second ?: return emptyList()
    require(value is JsonArray) { "$key is not a list" }
    return value.map { it.jsonPrimitive.content }
  }

  fun translations(key: String): Map<String, String> =
    messages[key].orEmpty().mapNotNull { (code, value) ->
      (value as? JsonPrimitive)?.takeIf { it.isString }?.let { code to it.content }
    }.toMap()

  private fun resolve(key: String, language: String): Pair<String, JsonElement>? {
    val values = messages[key] ?: return null
    val candidates = languageCandidates(language) + languageCandidates(fallbackLanguage)
    val code = candidates.firstOrNull { it in values } ?: values.keys.first()
    return code to values.getValue(code)
  }

  companion object {
    private val PLACEHOLDER = Regex("\\{\\{|\\}\\}|\\{([A-Za-z_][A-Za-z0-9_]*)\\}")
    private val CATEGORIES = PluralCategory.entries.map { it.name.lowercase() }.toSet()

    fun fromJson(files: List<String>, fallbackLanguage: String = "en"): TranslationCatalog {
      require(files.isNotEmpty()) { "At least one locale file is required" }
      val languages = mutableListOf<CatalogLanguage>()
      val messages = linkedMapOf<String, MutableMap<String, JsonElement>>()
      for (file in files) {
        val json = Json.parseToJsonElement(file).jsonObject
        val code = normalizeLanguageCode(json.getValue("locale").jsonPrimitive.content)
        val name = json.getValue("nativeName").jsonPrimitive.content
        val rule = json["pluralRule"]?.jsonPrimitive?.contentOrNull ?: "other"
        require(languages.none { it.code == code }) { "Duplicate locale: $code" }
        require(name.isNotBlank() && PluralRules.contains(rule)) { "Invalid locale metadata: $code" }
        languages += CatalogLanguage(code, name, rule)
        for ((key, value) in json.getValue("messages").jsonObject) {
          require(key.isNotBlank()) { "An empty translation key in $code" }
          validateMessage(key, value)
          messages.getOrPut(key) { linkedMapOf() }[code] = value
        }
      }
      return TranslationCatalog(languages, messages, normalizeLanguageCode(fallbackLanguage))
    }

    fun fromLocalizedMessages(values: Map<String, Map<String, String>>): TranslationCatalog {
      val codes = values.values.flatMap { it.keys }.map(::normalizeLanguageCode).distinct()
      val languages = codes.map { CatalogLanguage(it, it, "other") }
      val messages = values.mapValues { (_, translations) ->
        translations.mapKeys { normalizeLanguageCode(it.key) }.mapValues { (_, value) ->
          JsonPrimitive(value.replace("{", "{{").replace("}", "}}"))
        }
      }
      return TranslationCatalog(languages, messages, "en")
    }

    private fun validateMessage(key: String, value: JsonElement) {
      when (value) {
        is JsonPrimitive -> require(value.isString) { "$key must be a string" }
        is JsonArray -> require(value.all { it is JsonPrimitive && it.isString }) { "$key must contain strings" }
        is JsonObject -> require(
          "other" in value && value.keys.all { it in CATEGORIES } &&
            value.values.all { it is JsonPrimitive && it.isString }
        ) { "Invalid plural forms for $key" }

        else -> error("Invalid translation $key")
      }
    }
  }
}

fun normalizeLanguageCode(code: String): String {
  val normalized = code.trim().replace('_', '-').lowercase()
  require(Regex("[a-z]{2,8}(?:-[a-z0-9]{1,8})*").matches(normalized) && normalized != "system") {
    "Invalid language code: $code"
  }
  return normalized
}

fun languageCandidates(code: String): List<String> =
  generateSequence(normalizeLanguageCode(code)) {
    it.substringBeforeLast('-', "").ifEmpty { null }
  }.toList()
