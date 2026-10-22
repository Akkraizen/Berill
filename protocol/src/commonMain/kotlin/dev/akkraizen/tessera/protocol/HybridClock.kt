package dev.akkraizen.tessera.protocol

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

class HybridClock(private val node: String, private val wallClock: () -> Long) {
  private val lock = SynchronizedObject()
  private var lastWall = 0L
  private var counter = 0

  fun tick(): String =
    synchronized(lock) {
      val wall = wallClock()
      if (wall > lastWall) {
        lastWall = wall
        counter = 0
      } else {
        counter++
      }
      encode(lastWall, counter, node)
    }

  fun observe(remote: String) {
    val (wall, count) = decode(remote) ?: return
    synchronized(lock) {
      if (wall > lastWall || (wall == lastWall && count > counter)) {
        lastWall = wall
        counter = count
      }
    }
  }

  companion object {
    fun encode(wall: Long, counter: Int, node: String): String =
      wall.toString().padStart(15, '0') + "-" + counter.toString().padStart(5, '0') + "-" + node

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
