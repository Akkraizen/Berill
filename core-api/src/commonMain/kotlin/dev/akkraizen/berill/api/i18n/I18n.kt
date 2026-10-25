package dev.akkraizen.berill.api.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.intl.Locale
import dev.akkraizen.berill.sdk.i18n.PluralRules
import dev.akkraizen.berill.sdk.i18n.TranslationCatalog
import dev.akkraizen.berill.sdk.i18n.languageCandidates
import dev.akkraizen.berill.sdk.i18n.normalizeLanguageCode
import berill.generated.localization.CoreApiCatalogData

private val appCatalog: TranslationCatalog by lazy { TranslationCatalog.fromJson(CoreApiCatalogData.files) }

class AppLanguage(code: String, val nativeName: String, val pluralRule: (Int) -> PluralCategory = { PluralCategory.Other }) {
  val code: String = normalizeLanguageCode(code)

  init {
    require(nativeName.isNotBlank()) { "A language needs a display name" }
  }

  override fun equals(other: Any?): Boolean = other is AppLanguage && code == other.code

  override fun hashCode(): Int = code.hashCode()

  override fun toString(): String = code

  companion object {
    private var registered: List<AppLanguage> by mutableStateOf(appCatalog.languages.map { locale ->
      AppLanguage(locale.code, locale.nativeName) { n -> PluralRules.category(locale.pluralRule, n) }
    })

    val entries: List<AppLanguage>
      get() = registered

    fun register(language: AppLanguage): AppLanguage {
      require(registered.none { it.code == language.code }) { "Language ${language.code} is already registered" }
      registered = registered + language
      return language
    }

    fun fromCode(code: String?): AppLanguage? {
      val candidates = code?.let { runCatching { languageCandidates(it) }.getOrNull() } ?: return null
      return candidates.firstNotNullOfOrNull { candidate -> registered.firstOrNull { it.code == candidate } }
    }

    fun system(): AppLanguage = fromCode(runCatching { Locale.current.toLanguageTag() }.getOrNull())
      ?: fromCode(appCatalog.fallbackLanguage)
      ?: registered.first()
  }
}

object I18n {
  val catalog: TranslationCatalog
    get() = appCatalog
  var language: AppLanguage by mutableStateOf(AppLanguage.system())

  fun text(key: String, vararg arguments: Pair<String, Any?>): String = catalog.text(key, language.code, arguments.toMap())

  fun plural(key: String, count: Int, vararg arguments: Pair<String, Any?>): String =
    catalog.text(key, language.code, arguments.toMap(), count) { code, n ->
      AppLanguage.fromCode(code)?.pluralRule?.invoke(n) ?: PluralCategory.Other
    }

  fun list(key: String): List<String> = catalog.list(key, language.code)
}

class Tr(
  val key: String,
  vararg arguments: Pair<String, Any?>,
  private val catalog: TranslationCatalog = I18n.catalog
) {
  private val arguments = arguments.toMap()
  val translations: Map<String, String>
    get() = catalog.translations(key)

  init {
    require(key.isNotBlank()) { "A translation key is required" }
  }

  fun get(language: AppLanguage): String = get(language.code)

  fun get(languageCode: String): String = catalog.text(key, languageCode, arguments)

  operator fun invoke(): String = get(I18n.language)

  override fun toString(): String = invoke()
}
