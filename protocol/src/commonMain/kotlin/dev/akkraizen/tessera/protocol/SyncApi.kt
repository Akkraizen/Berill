package dev.akkraizen.tessera.protocol

import kotlinx.serialization.Serializable

@Serializable
data class SyncRequest(val deviceId: String, val since: Long, val changes: List<SyncRecord> = emptyList())

@Serializable
data class SyncResponse(val records: List<SyncRecord>, val cursor: Long, val hasMore: Boolean = false)

object Collections {
  const val NOTES = "notes"
  const val SETTINGS = "settings"
}
