package dev.akkraizen.berill.plugins.finance

import dev.akkraizen.berill.plugins.finance.ui.automationText
import kotlin.test.Test
import kotlin.test.assertEquals

class AutomationLocalizationTest {
  @Test fun automationUsesTheFinanceCatalog() {
    assertEquals("Подписки и бюджеты", automationText("title", "ru"))
    assertEquals("Удалить", automationText("delete", "ru"))
    assertEquals("Delete", automationText("delete", "en"))
  }
}
