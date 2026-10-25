package dev.akkraizen.berill.server

internal val STATUS_PAGE: String by lazy {
  object {}.javaClass.getResourceAsStream("/status.html")!!.use { it.readBytes().decodeToString() }
}
