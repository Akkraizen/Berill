package dev.akkraizen.berill.protocol

import kotlinx.serialization.Serializable

@Serializable
data class SyncRequest(
  val deviceId: String,
  val since: Long,
  val changes: List<SyncRecord> = emptyList(),
  val client: ClientInfo? = null
)

@Serializable
data class ClientInfo(val name: String, val platform: String, val version: String)

@Serializable
data class SyncResponse(
  val records: List<SyncRecord>,
  val cursor: Long,
  val hasMore: Boolean = false,
  val dataset: String? = null,
  val user: String? = null
)

@Serializable
data class LatestRelease(
  val version: String,
  val android: String? = null,
  val windows: String? = null
)

@Serializable
data class DeviceStatus(
  val deviceId: String,
  val name: String? = null,
  val platform: String? = null,
  val version: String? = null,
  val firstSeen: Long,
  val lastSync: Long
)

@Serializable
data class ServerStatus(
  val serverVersion: String,
  val user: String,
  val records: Long,
  val deleted: Long,
  val devices: List<DeviceStatus>
)

object Collections {
  const val NOTES = "notes"
  const val SETTINGS = "settings"
}
