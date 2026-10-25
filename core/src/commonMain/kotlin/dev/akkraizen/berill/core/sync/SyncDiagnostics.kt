package dev.akkraizen.berill.core.sync

data class SyncDiagnostics(
  val serverUrl: String?,
  val hasToken: Boolean,
  val user: String?,
  val dataset: String?,
  val cursor: Long,
  val fullResendPending: Boolean,
  val pending: Int,
  val pendingExcluded: Int,
  val totalRecords: Long,
  val lastOkMillis: Long?,
  val lastError: String?,
  val lastErrorMillis: Long?,
  val deviceId: String,
  val encryption: E2eState
)
