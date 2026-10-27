package dev.akkraizen.berill.plugins.habits.i18n

import dev.akkraizen.berill.api.i18n.AppLanguage
import dev.akkraizen.berill.api.i18n.I18n
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizationTest {
  @Test
  fun `plugin loads its generated catalog without global registration`() {
    assertTrue("habits.title" in pluginI18n.keys)
    assertTrue("ui.cancel" !in pluginI18n.keys)
    assertTrue("habits.title" !in I18n.catalog.keys)
    assertEquals("Привычки", HabitsText.title.get("ru"))
    assertEquals("Habits", HabitsText.title.get("en"))
    assertEquals("Выполнено 2 из 3", HabitsText.doneOf(2, 3).get("ru"))
    assertEquals("2 of 3 done", HabitsText.doneOf(2, 3).get("en"))
  }

  @Test
  fun `plugin plural forms follow interface language and fallback`() {
    val previous = I18n.language
    try {
      I18n.language = AppLanguage.fromCode("ru")!!
      assertEquals("22 задачи", HabitsText.problems(22))
      I18n.language = AppLanguage.fromCode("en")!!
      assertEquals("22 problems", HabitsText.problems(22))
      I18n.language = AppLanguage("de", "Deutsch")
      assertEquals("22 problems", HabitsText.problems(22))
      assertEquals("Habits", HabitsText.title())
    } finally {
      I18n.language = previous
    }
  }
}
