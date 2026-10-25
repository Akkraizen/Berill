package dev.akkraizen.berill.api.i18n

import dev.akkraizen.berill.sdk.i18n.TranslationCatalog

class Localization(files: List<String>, fallbackLanguage: String = "en") {
  private val catalog = TranslationCatalog.fromJson(files, fallbackLanguage)

  val keys: Set<String>
    get() = catalog.keys

  fun tr(key: String, vararg arguments: Pair<String, Any?>): Tr = Tr(key, *arguments, catalog = catalog)

  fun text(key: String, vararg arguments: Pair<String, Any?>): String =
    catalog.text(key, I18n.language.code, arguments.toMap())

  fun plural(key: String, count: Int, vararg arguments: Pair<String, Any?>): String =
    catalog.text(key, I18n.language.code, arguments.toMap(), count)

  fun list(key: String): List<String> = catalog.list(key, I18n.language.code)
}
