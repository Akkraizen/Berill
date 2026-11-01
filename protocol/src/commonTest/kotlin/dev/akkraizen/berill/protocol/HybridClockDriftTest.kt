package dev.akkraizen.berill.protocol

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HybridClockDriftTest {
  @Test
  fun farFutureStampsAreNotFollowed() {
    var now = 1_000_000_000_000L
    val clock = HybridClock("a") { now }
    val nearFuture = HybridClock.encode(now + 60_000, 3, "b")
    clock.observe(nearFuture)
    assertTrue(clock.tick() > nearFuture, "small drift is still followed")

    val year2100 = HybridClock.encode(4_102_444_800_000L, 0, "broken")
    assertTrue(clock.isFromFuture(year2100))
    clock.observe(year2100)
    val next = clock.tick()
    assertTrue(next < year2100, "a wrong clock elsewhere must not move this one")
    assertFalse(clock.isFromFuture(next))
    val edit = clock.stampAbove(listOf(year2100))
    assertTrue(edit > year2100 && edit.startsWith(year2100.substringBefore('-')), "edits still win, as a successor")
    assertTrue(clock.stampAbove(listOf(next)) > next)
    now += 2 * HybridClock.MAX_DRIFT_MILLIS
    assertFalse(clock.isFromFuture(HybridClock.encode(now, 0, "b")))
  }
}
