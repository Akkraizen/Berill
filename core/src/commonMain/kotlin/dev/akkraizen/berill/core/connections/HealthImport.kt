package dev.akkraizen.berill.core.connections

import kotlinx.coroutines.flow.StateFlow

interface HealthImport {
  val status: StateFlow<String>
  suspend fun connect()
  suspend fun refresh()
}
