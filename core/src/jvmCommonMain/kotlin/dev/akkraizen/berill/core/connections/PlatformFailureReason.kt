package dev.akkraizen.berill.core.connections
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.AccessDeniedException
internal actual fun platformFailureReason(error: Throwable): FailureReason? = when (error) {
  is SocketException, is SocketTimeoutException, is UnknownHostException -> FailureReason.NETWORK
  is SecurityException, is AccessDeniedException -> FailureReason.ACCESS_DENIED
  is IOException -> FailureReason.IO
  else -> null
}