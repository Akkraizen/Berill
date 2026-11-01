package dev.akkraizen.berill.server

import dev.akkraizen.berill.protocol.HybridClock
import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ClockSkewTest {
  private val now = 1_800_000_000_000L
  private fun record(stamp: String, deletedAt: String? = null) =
    SyncRecord("notes", "n1", JsonObject(mapOf("body" to JsonPrimitive("x"))), mapOf("body" to stamp), deletedAt)

  @Test
  fun stampsFarAheadAreRejectedUnlessTheyContinueAStoredFutureTime() {
    val normal = HybridClock.encode(now + 60_000, 0, "a")
    checkClockSkew(record(normal), null, now)

    val future = HybridClock.encode(now + 3 * HybridClock.MAX_DRIFT_MILLIS, 0, "skewed")
    assertFailsWith<ClockSkewException> { checkClockSkew(record(future), null, now) }
    assertFailsWith<ClockSkewException> { checkClockSkew(record(normal, deletedAt = future), record(normal), now) }

    // Data written before this check may already carry a future time; edits continue it.
    val successor = HybridClock.encode(now + 3 * HybridClock.MAX_DRIFT_MILLIS, 1, "a")
    checkClockSkew(record(successor), record(future), now)
  }
}
