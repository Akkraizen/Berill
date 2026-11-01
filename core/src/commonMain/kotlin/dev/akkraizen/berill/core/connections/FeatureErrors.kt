package dev.akkraizen.berill.core.connections

import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.log.AppLog
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException

@kotlinx.serialization.Serializable
enum class FailureReason { UNEXPECTED, NETWORK, ACCESS_DENIED, AUTH_REQUIRED, INVALID_DATA, MISSING_ATTACHMENT, CORRUPT_ATTACHMENT, TOO_LARGE, IO, SERVICE_UNAVAILABLE, SERVER_NOT_FOUND, ENCRYPTION_LOCKED }
@kotlinx.serialization.Serializable
enum class FailureOperation { SEARCH, DAY, TRASH, RESTORE, DELETE, COMMAND, EXPORT, IMPORT, HEALTH, REMINDERS, ATTACHMENT, SHARE }
class FeatureException(val reason: FailureReason, cause: Throwable? = null) : Exception(reason.name, cause)

fun checkServiceStatus(status: Int) {
  if (status in 200..299) return
  throw FeatureException(when (status) {
    401 -> FailureReason.AUTH_REQUIRED
    403 -> FailureReason.ACCESS_DENIED
    404 -> FailureReason.SERVER_NOT_FOUND
    408 -> FailureReason.NETWORK
    413 -> FailureReason.TOO_LARGE
    429, in 500..599 -> FailureReason.SERVICE_UNAVAILABLE
    else -> FailureReason.UNEXPECTED
  })
}

internal expect fun platformFailureReason(error: Throwable): FailureReason?
fun failureReason(error: Throwable): FailureReason {
  var current: Throwable? = error
  repeat(12) {
    val cause = current ?: return FailureReason.UNEXPECTED
    when (cause) {
      is FeatureException -> return cause.reason
      is dev.akkraizen.berill.api.HttpException -> return when (cause.kind) {
        dev.akkraizen.berill.api.HttpFailureKind.NETWORK, dev.akkraizen.berill.api.HttpFailureKind.TIMEOUT -> FailureReason.NETWORK
        dev.akkraizen.berill.api.HttpFailureKind.INVALID_REQUEST, dev.akkraizen.berill.api.HttpFailureKind.INVALID_RESPONSE -> FailureReason.INVALID_DATA
        else -> when (cause.status) { 401 -> FailureReason.AUTH_REQUIRED; 403 -> FailureReason.ACCESS_DENIED; 404 -> FailureReason.SERVER_NOT_FOUND; 429, in 500..599 -> FailureReason.SERVICE_UNAVAILABLE; else -> FailureReason.UNEXPECTED }
      }
      is HttpRequestTimeoutException -> return FailureReason.NETWORK
      is ResponseException -> return when (cause.response.status.value) {
        401 -> FailureReason.AUTH_REQUIRED
        403 -> FailureReason.ACCESS_DENIED
        404 -> FailureReason.SERVER_NOT_FOUND
        408 -> FailureReason.NETWORK
        413 -> FailureReason.TOO_LARGE
        429, in 500..599 -> FailureReason.SERVICE_UNAVAILABLE
        else -> FailureReason.UNEXPECTED
      }
      is SerializationException, is IllegalArgumentException -> return FailureReason.INVALID_DATA
    }
    platformFailureReason(cause)?.let { return it }
    current = cause.cause
  }
  return FailureReason.UNEXPECTED
}
fun failureMessage(error: Throwable): String = I18n.text("errors.reason.${failureReason(error).name}")

data class FeatureProblem(val operation: FailureOperation, val reason: FailureReason) {
  fun title(): String = I18n.text("errors.failed", "operation" to I18n.text("errors.operation.${operation.name}"))
  fun message(): String = I18n.text("errors.reason.${reason.name}")
}

enum class FeatureProgress { RUNNING, SUCCESS, CANCELLED }

class FeatureErrors(private val onProgress: (FailureOperation, FeatureProgress) -> Unit = { _, _ -> }, private val onReport: (FeatureProblem) -> Unit = {}) {
  private val _problem = MutableStateFlow<FeatureProblem?>(null)
  val problem = _problem.asStateFlow()
  fun dismiss() { _problem.value = null }
  fun report(operation: FailureOperation, error: Exception) {
    if (error is CancellationException) throw error
    AppLog.error("features", operation.name, error)
    val problem = FeatureProblem(operation, failureReason(error))
    _problem.value = problem
    onReport(problem)
  }
  fun run(operation: FailureOperation, block: () -> Unit): Boolean = try {
    block(); true
  } catch (e: CancellationException) { throw e } catch (e: Exception) { report(operation, e); false }
  suspend fun runSuspending(operation: FailureOperation, block: suspend () -> Unit): Boolean {
    onProgress(operation, FeatureProgress.RUNNING)
    return try {
      block(); onProgress(operation, FeatureProgress.SUCCESS); true
    } catch (e: CancellationException) { onProgress(operation, FeatureProgress.CANCELLED); throw e }
      catch (e: Exception) { report(operation, e); false }
  }
}