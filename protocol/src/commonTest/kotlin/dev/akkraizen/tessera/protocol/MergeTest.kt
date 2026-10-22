package dev.akkraizen.tessera.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MergeTest {
  private fun rec(
    vararg fields: Pair<String, Pair<Int, String>>,
    deletedAt: String? = null,
  ) =
    SyncRecord(
      collection = "habit_days",
      id = "2026-10-19",
      fields = JsonObject(fields.associate { (k, v) -> k to JsonPrimitive(v.first) }),
      clocks = fields.associate { (k, v) -> k to v.second },
      deletedAt = deletedAt,
    )

  @Test
  fun differentFieldsFromTwoDevicesAreBothKept() {
    val phone = rec("chess" to (30 to "000000000000001-00000-phone"))
    val pc = rec("deutsch" to (20 to "000000000000002-00000-pc"))
    val merged = phone.mergeWith(pc)
    assertEquals(JsonPrimitive(30), merged.fields["chess"])
    assertEquals(JsonPrimitive(20), merged.fields["deutsch"])
  }

  @Test
  fun laterWriteWinsForSameField() {
    val a = rec("chess" to (30 to "000000000000001-00000-a"))
    val b = rec("chess" to (0 to "000000000000005-00000-b"))
    assertEquals(JsonPrimitive(0), a.mergeWith(b).fields["chess"])
    assertEquals(JsonPrimitive(0), b.mergeWith(a).fields["chess"])
  }

  @Test
  fun mergeIsCommutativeAndIdempotent() {
    val a = rec("x" to (1 to "000000000000003-00000-a"), "y" to (2 to "000000000000001-00000-a"))
    val b = rec("x" to (9 to "000000000000002-00000-b"), "y" to (8 to "000000000000004-00000-b"))
    assertEquals(a.mergeWith(b), b.mergeWith(a))
    assertEquals(a.mergeWith(b), a.mergeWith(b).mergeWith(b))
  }

  @Test
  fun tombstoneHidesOlderFieldsButNotNewerOnes() {
    val alive = rec("x" to (1 to "000000000000001-00000-a"))
    val deleted = alive.mergeWith(alive.copy(deletedAt = "000000000000002-00000-b"))
    assertTrue(deleted.deleted)
    val revived = deleted.mergeWith(rec("x" to (2 to "000000000000003-00000-a")))
    assertFalse(revived.deleted)
  }

  @Test
  fun clockIsMonotonic() {
    var now = 100L
    val clock = HybridClock("t") { now }
    val first = clock.tick()
    val second = clock.tick()
    now = 50
    val third = clock.tick()
    assertTrue(first < second && second < third)
    clock.observe(HybridClock.encode(1_000, 3, "other"))
    assertTrue(clock.tick() > HybridClock.encode(1_000, 3, "other"))
  }
}
