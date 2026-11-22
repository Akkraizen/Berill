package dev.akkraizen.berill.core.connections
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.AccessDeniedException
import javax.net.ssl.SSLException
internal actual fun platformFailureReason(error: Throwable): FailureReason? = when (error) {
  // TLS and HTTP protocol failures are IOExceptions too, but they come from the connection, not from local files.
  is SocketException, is SocketTimeoutException, is UnknownHostException, is SSLException, is ProtocolException -> FailureReason.NETWORK
  is SecurityException, is AccessDeniedException -> FailureReason.ACCESS_DENIED
  is IOException -> FailureReason.IO
  else -> null
}