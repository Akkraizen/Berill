package dev.akkraizen.berill.core

import dev.akkraizen.berill.api.i18n.AppLanguage
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.i18n.Localization
import dev.akkraizen.berill.api.i18n.PluralCategory
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.DAYS_SHORT
import dev.akkraizen.berill.api.ui.MONTHS_GEN
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.core.ui.theme.Appearance
import dev.akkraizen.berill.sdk.i18n.TranslationCatalog
import dev.akkraizen.berill.sdk.protocol.LocalizedText
import dev.akkraizen.berill.sdk.protocol.ManifestPage
import dev.akkraizen.berill.sdk.protocol.PluginManifest
import dev.akkraizen.berill.sdk.protocol.problems
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class I18nTest {
  private val russian = AppLanguage.fromCode("ru")!!
  private val english = AppLanguage.fromCode("en")!!

  private fun file(locale: String): String = javaClass.getResource("/i18n/$locale.json")!!.readText()

  private fun catalog(fallback: String = "en") =
    TranslationCatalog.fromJson(listOf("en", "ru", "fr", "fr-CA").map(::file), fallback)

  @Test
  fun `keys resolve from locale files including third and regional locales`() {
    val translations = catalog()
    assertEquals("Bonjour", translations.text("hello", "fr"))
    assertEquals("Bonjour", TranslationCatalog.fromJson(listOf(file("fr"))).text("hello", "en"))
    assertEquals("Allô", translations.text("hello", "FR_ca"))
    assertEquals("Bonjour", translations.text("hello", "fr-BE"))
    assertEquals("Hello", translations.text("hello", "de"))
    assertEquals("Français", translations.text("frenchOnly", "ja"))
    assertEquals("English fallback", translations.text("englishOnly", "ru"))
    assertEquals("Привет", catalog("ru-RU").text("hello", "de"))
    assertEquals(listOf("en", "ru", "fr", "fr-ca"), translations.languages.map { it.code })
  }

  @Test
  fun `named parameters and escaped braces preserve their values`() {
    val translations = catalog()
    val name = "{literal} $ " + "\\folder\nsecond line"
    assertEquals("Hello, $name!", translations.text("greet", "en", mapOf("name" to name)))
    assertEquals("{name}: Berill", translations.text("braces", "en", mapOf("name" to "Berill")))
    assertEquals("", translations.text("empty", "en"))
    assertEquals("missing.key", translations.text("missing.key", "en"))
    assertFailsWith<IllegalArgumentException> { translations.text("greet", "en") }
  }

  @Test
  fun `plural rules come from the file of the selected message`() {
    val translations = catalog()
    assertEquals("1 задача", translations.text("tasks", "ru", count = 1))
    assertEquals("22 задачи", translations.text("tasks", "ru", count = 22))
    assertEquals("11 задач", translations.text("tasks", "ru", count = 11))
    assertEquals("-22 задачи", translations.text("tasks", "ru", count = -22))
    assertEquals("${Int.MIN_VALUE} задач", translations.text("tasks", "ru", count = Int.MIN_VALUE))
    assertEquals("0 tâche", translations.text("tasks", "fr", count = 0))
    assertEquals("2 tâches", translations.text("tasks", "fr-CA", count = 2))
    assertEquals("21 items", translations.text("fallbackTasks", "ru", count = 21))
    assertEquals("1 item", translations.text("fallbackTasks", "fr", count = 1))
    assertFailsWith<IllegalArgumentException> { translations.text("tasks", "en") }
  }

  @Test
  fun `list messages use the same fallback and enforce their type`() {
    val translations = catalog()
    assertEquals(listOf("Пн", "Вт"), translations.list("weekdays", "ru-RU"))
    assertEquals(listOf("Mon", "Tue"), translations.list("weekdays", "fr"))
    assertEquals(emptyList(), translations.list("missing.key", "en"))
    assertFailsWith<IllegalArgumentException> { translations.list("hello", "en") }
    assertFailsWith<IllegalStateException> { translations.text("weekdays", "en") }
  }

  @Test
  fun `invalid catalogs are rejected instead of silently overriding translations`() {
    assertFailsWith<IllegalArgumentException> { TranslationCatalog.fromJson(emptyList()) }
    assertFailsWith<IllegalArgumentException> { TranslationCatalog.fromJson(listOf(file("fr"), file("fr"))) }
    assertFailsWith<IllegalArgumentException> {
      TranslationCatalog.fromJson(listOf(file("fr").replace("zero-one-other", "unknown-rule")))
    }
    assertFailsWith<IllegalArgumentException> { AppLanguage("", "Invalid") }
  }

  @Test
  fun `Tr stores a key and resolves its arguments on the current language`() {
    val text = Tr("greet", "name" to "Berill", catalog = catalog())
    assertEquals("greet", text.key)
    assertEquals("Привет, Berill!", text.get(russian))
    assertEquals("Hello, Berill!", text.get(english))
    val previous = I18n.language
    try {
      I18n.language = english
      assertEquals("Hello, Berill!", text())
      I18n.language = russian
      assertEquals("Привет, Berill!", text())
    } finally {
      I18n.language = previous
    }
  }

  @Test
  fun `registered languages appear in saved appearance and language choices`() {
    val language = AppLanguage.register(AppLanguage("zz", "Test language") { PluralCategory.Other })
    assertTrue(language in AppLanguage.entries)
    assertEquals(language, AppLanguage.fromCode("ZZ_region"))
    assertEquals(language, Appearance(language = "zz").appLanguage)
    assertEquals(russian, AppLanguage.fromCode("ru-RU"))
    assertEquals(null, AppLanguage.fromCode("invalid/code"))
    assertFailsWith<IllegalArgumentException> { AppLanguage.register(AppLanguage("zz", "Duplicate")) }
  }

  @Test
  fun `bundled catalog is generated from JSON and keeps existing UI text and dates`() {
    assertTrue("ui.cancel" in I18n.catalog.keys)
    assertTrue("habits.title" !in I18n.catalog.keys)
    assertEquals("Отмена", Tr("ui.cancel").get(russian))
    assertEquals("Cancel", Tr("ui.cancel").get(english))
    val previous = I18n.language
    try {
      I18n.language = russian
      assertEquals("2 января", LocalDate(2026, 1, 2).dayMonth())
      assertEquals("Пн", DAYS_SHORT.first())
      I18n.language = english
      assertEquals("January 2", LocalDate(2026, 1, 2).dayMonth())
      assertEquals("January", MONTHS_GEN.first())
    } finally {
      I18n.language = previous
    }
  }

  @Test
  fun `module catalogs resolve identical keys independently`() {
    val en = Localization(listOf(file("en")))
    val ru = Localization(listOf(file("ru")))
    val englishText = en.tr("hello")
    val russianText = ru.tr("hello")
    assertEquals("Hello", englishText.get("ru"))
    assertEquals("Привет", russianText.get("en"))
    assertTrue("hello" !in I18n.catalog.keys)
    val previous = I18n.language
    try {
      I18n.language = english
      assertEquals("Hello", englishText())
      assertEquals("Привет", russianText())
      assertEquals("Hello, Berill!", en.text("greet", "name" to "Berill"))
      assertEquals("22 задачи", ru.plural("tasks", 22))
      assertEquals(listOf("Пн", "Вт"), ru.list("weekdays"))
      I18n.language = russian
      assertEquals("Hello", englishText())
      assertEquals("Привет", russianText())
    } finally {
      I18n.language = previous
    }
  }

  @Test
  fun `plugin manifest translations remain in JSON without required ru or en`() {
    val title = Json.decodeFromString<LocalizedText>("""{"fr":"Compteurs"}""")
    val manifest = PluginManifest(
      id = "counters",
      version = "1.0",
      name = title,
      entry = "com.example.CountersPlugin",
      pages = listOf(ManifestPage("main", title))
    )
    assertTrue(manifest.problems().isEmpty())
    assertEquals(manifest, Json.decodeFromString<PluginManifest>(Json.encodeToString(manifest)))
    val catalog = TranslationCatalog.fromLocalizedMessages(mapOf("pages.main" to title.translations))
    assertEquals("Compteurs", catalog.text("pages.main", "fr-CA"))
  }
}
