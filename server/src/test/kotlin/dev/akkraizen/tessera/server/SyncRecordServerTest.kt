package dev.akkraizen.tessera.server

import dev.akkraizen.tessera.protocol.SyncRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncRecordServerTest {
  @Test
  fun serverMergeKeepsFieldsFromBothDevices() {
    val a = SyncRecord(
      "habit_days",
      "2026-10-20",
      JsonObject(mapOf("chess" to JsonPrimitive(30))),
      mapOf("chess" to "000000000000001-00000-a")
    )
    val b = SyncRecord(
      "habit_days",
      "2026-10-20",
      JsonObject(mapOf("deutsch" to JsonPrimitive(20))),
      mapOf("deutsch" to "000000000000002-00000-b")
    )
    assertEquals(setOf("chess", "deutsch"), a.mergeWith(b).fields.keys)
  }
}
