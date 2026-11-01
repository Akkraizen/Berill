package dev.akkraizen.berill.core

import com.sun.net.httpserver.HttpServer
import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.i18n.*
import dev.akkraizen.berill.api.ui.search.networkErrorText
import dev.akkraizen.berill.core.data.KtorHttpAccess
import java.net.InetSocketAddress
import kotlinx.coroutines.*
import kotlin.test.*

class HttpAccessErrorsTest {
 @Test fun existingPluginConstructorRemainsBinaryCompatible() {
  val constructor = HttpException::class.java.getConstructor(Int::class.javaPrimitiveType!!, String::class.java)
  assertEquals(401, constructor.newInstance(401, "HTTP 401").status)
 }

 @Test fun httpStatusesAndInvalidJsonAreHandledWithoutBreakingLowLevelCalls() = runBlocking {
  val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  server.createContext("/") { call ->
   val code = call.requestURI.path.removePrefix("/").toIntOrNull() ?: 200
   val body = "not-json secret response".encodeToByteArray()
   call.sendResponseHeaders(code, body.size.toLong())
   call.responseBody.use { it.write(body) }
  }
  server.start()
  try {
   val http = KtorHttpAccess()
   val url = "http://127.0.0.1:${server.address.port}"
   for (status in listOf(401, 403, 404, 429, 503)) {
    val error = assertFailsWith<HttpException> { http.getJson("$url/$status") }
    assertEquals(status, error.status)
    assertFalse(networkErrorText(error).contains("secret response"))
   }
   val malformed = assertFailsWith<HttpException> { http.getJson("$url/200") }
   assertEquals(HttpFailureKind.INVALID_RESPONSE, malformed.kind)
   val response = http.send(HttpRequest("GET", "$url/401"))
   assertEquals(401, response.status)
   assertFalse(response.ok)
  } finally { server.stop(0) }
 }
 @Test fun cancellationPropagatesAndTransportFailuresAreTyped() = runBlocking {
  val http = KtorHttpAccess()
  assertFailsWith<TimeoutCancellationException> { withTimeout(1) { http.getJson("http://127.0.0.1:1/") } }
  val error = assertFailsWith<HttpException> { http.getJson("http://127.0.0.1:1/") }
  assertEquals(HttpFailureKind.NETWORK, error.kind)
 }
 @Test fun allApiMessagesAreLocalizedAndDoNotExposeRawErrors() {
  val previous = I18n.language
  try { for (language in listOf("ru", "en")) {
   I18n.language = AppLanguage.fromCode(language)!!
   for (kind in HttpFailureKind.entries) {
    val message = networkErrorText(HttpException(401, "secret-token", kind))
    assertFalse(message.contains("secret-token"))
    assertFalse(message.startsWith("ui."))
   }
   assertFalse(networkErrorText(IllegalStateException("secret-token")).contains("secret-token"))
  } } finally { I18n.language = previous }
 }
}
