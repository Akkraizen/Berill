package dev.akkraizen.berill.core.update

enum class UpdateFailure { DOWNLOAD, INTEGRITY, PACKAGE, INSTALL, UNSUPPORTED, INSECURE, SERVER_TOO_OLD, SIGNATURE }
class UpdateException(val reason: UpdateFailure) : Exception(reason.name)
enum class InstallResult { OPENED, PERMISSION_REQUIRED, EXIT_REQUIRED }
interface UpdateInstaller {
  val available: Boolean get() = false
  val previousFailure: UpdateFailure? get() = null
  val platform: String
  suspend fun download(update: AppUpdate, progress: (Long, Long?) -> Unit): String
  suspend fun validate(path: String, update: AppUpdate)
  suspend fun install(path: String, update: AppUpdate): InstallResult
  suspend fun remove(path: String)
}
object NoUpdateInstaller : UpdateInstaller {
  override val platform = ""
  override suspend fun download(update: AppUpdate, progress: (Long, Long?) -> Unit): String = throw UpdateException(UpdateFailure.UNSUPPORTED)
  override suspend fun validate(path: String, update: AppUpdate) { throw UpdateException(UpdateFailure.UNSUPPORTED) }
  override suspend fun install(path: String, update: AppUpdate): InstallResult = throw UpdateException(UpdateFailure.UNSUPPORTED)
  override suspend fun remove(path: String) {}
}
sealed interface UpdateTransfer {
  data object Idle : UpdateTransfer
  data class Downloading(val update: AppUpdate, val received: Long, val total: Long?) : UpdateTransfer
  data class Ready(val update: AppUpdate, val path: String, val hint: InstallResult? = null) : UpdateTransfer
  data class Installing(val update: AppUpdate) : UpdateTransfer
  data class Failed(val reason: UpdateFailure, val ready: Ready? = null) : UpdateTransfer
}
