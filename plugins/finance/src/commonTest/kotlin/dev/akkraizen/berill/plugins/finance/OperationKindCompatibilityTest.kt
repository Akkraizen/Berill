package dev.akkraizen.berill.plugins.finance
import dev.akkraizen.berill.plugins.finance.data.*
import kotlinx.serialization.json.*
import kotlin.test.*
class OperationKindCompatibilityTest {
 @Test fun wireAndSettingsKeysStayCompatible() { for ((v, legacy) in mapOf(OperationKind.EXPENSE to "расход", OperationKind.INCOME to "доход", OperationKind.DEPOSIT to "пополнение", OperationKind.WITHDRAW to "снятие")) {
  val old = JsonPrimitive(legacy)
  assertEquals(v, Json.decodeFromJsonElement(OperationKindSerializer, old))
  assertEquals(old, Json.encodeToJsonElement(OperationKindSerializer, v))
  assertEquals("categories.$legacy", categoriesKey(v))
 } }
}
