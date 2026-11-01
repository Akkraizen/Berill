package dev.akkraizen.berill.protocol

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

class HybridClock(private val node: String, private val wallClock: () -> Long) {
  private val lock = SynchronizedObject()
  private var lastWall = 0L
  private var counter = 0

  fun tick(): String =
    synchronized(lock) {
      val wall = wallClock()
      // A last value far ahead means this device's clock was wrong and has been corrected.
      if (wall > lastWall || lastWall > wall + MAX_DRIFT_MILLIS) {
        lastWall = wall
        counter = 0
      } else {
        counter++
      }
      encode(lastWall, counter, node)
    }

  /**
   * Moves the clock past a stamp seen on another device. Stamps more than [MAX_DRIFT_MILLIS] ahead of
   * this device's time come from a device whose clock is wrong; following them would make every
   * later edit here carry that future time, so they are not followed.
   */
  fun observe(remote: String) {
    val (wall, count) = decode(remote) ?: return
    if (wall > wallClock() + MAX_DRIFT_MILLIS) return
    synchronized(lock) {
      if (wall > lastWall || (wall == lastWall && count > counter)) {
        lastWall = wall
        counter = count
      }
    }
  }

  /**
   * A stamp that beats both a new [tick] and every one of [existing] (field clocks or a tombstone).
   * Normally that is just the tick. Data written earlier by a device whose clock ran far ahead keeps
   * its future stamps; an edit then takes the next counter value of that time instead of inventing an
   * even later one, which the server accepts as a successor.
   */
  fun stampAbove(existing: Collection<String>): String {
    val stamp = tick()
    val highest = existing.maxOrNull() ?: return stamp
    if (highest < stamp) return stamp
    val (wall, count) = decode(highest) ?: return stamp
    return encode(wall, count + 1, node)
  }

  /** True if [stamp] is further ahead of this device's time than clocks are allowed to drift. */
  fun isFromFuture(stamp: String): Boolean = (decode(stamp)?.first ?: 0L) > wallClock() + MAX_DRIFT_MILLIS

  companion object {
    const val MAX_DRIFT_MILLIS: Long = 24L * 60 * 60 * 1000

    fun encode(
      wall: Long,
      counter: Int,
      node: String
    ): String = wall.toString().padStart(15, '0') + "-" + counter.toString().padStart(5, '0') + "-" + node

    fun decode(stamp: String): Pair<Long, Int>? {
      val parts = stamp.split("-")

      if (parts.size < 3)
        return null

      val wall = parts[0].toLongOrNull()
        ?: return null

      val count = parts[1].toIntOrNull()
        ?: return null

      return wall to count
    }
  }
}
