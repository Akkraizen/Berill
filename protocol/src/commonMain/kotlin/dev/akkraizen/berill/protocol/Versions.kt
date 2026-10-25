package dev.akkraizen.berill.protocol

fun compareVersions(a: String, b: String): Int {
  val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
  val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
  for (i in 0 until maxOf(x.size, y.size)) {
    val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
    if (c != 0) return c
  }
  return 0
}
