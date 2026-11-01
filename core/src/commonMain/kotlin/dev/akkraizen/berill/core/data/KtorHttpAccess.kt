package dev.akkraizen.berill.core.data

import dev.akkraizen.berill.api.HttpAccess
import dev.akkraizen.berill.api.HttpException
import dev.akkraizen.berill.api.HttpFailureKind
import kotlinx.coroutines.CancellationException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.api.HttpRequest
import dev.akkraizen.berill.api.HttpResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class KtorHttpAccess : HttpAccess {
  private val client = HttpClient {
    install(HttpTimeout) {
      requestTimeoutMillis = 20_000
      connectTimeoutMillis = 10_000
      socketTimeoutMillis = 20_000
    }
    expectSuccess = false
  }

  override suspend fun getJson(url: String, headers: Map<String, String>): JsonElement {
    val extra = headers
    val response = transport { client.get(url) {
      header(HttpHeaders.UserAgent, USER_AGENT)
      header(HttpHeaders.Accept, "application/json")
      extra.forEach { (name, value) -> header(name, value) }
    } }
    val status = response.status.value
    if (status !in 200..299) { AppLog.warn("api", "JSON request returned HTTP $status"); throw HttpException(status) }
    val body = transport { response.bodyAsText() }
    return try { Json.parseToJsonElement(body) }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { throw HttpException(status, kind = HttpFailureKind.INVALID_RESPONSE) }
  }

  override suspend fun send(request: HttpRequest): HttpResponse {
    val response = transport { client.request(request.url) {
      method = HttpMethod.parse(request.method.uppercase())
      header(HttpHeaders.UserAgent, USER_AGENT)
      request.headers.forEach { (name, value) -> header(name, value) }
      val body = request.body
      if (body != null)
        setBody(TextContent(body, ContentType.parse(request.contentType ?: "application/json")))
    } }
    // Non-success statuses are returned for callers that refresh credentials or handle conflicts.
    if (response.status.value !in 200..299) AppLog.warn("api", "request returned HTTP ${response.status.value}")
    return HttpResponse(response.status.value, transport { response.bodyAsText() })
  }

  private suspend fun <T> transport(block: suspend () -> T): T = try { block() }
  catch (e: CancellationException) { throw e }
  catch (e: HttpException) { throw e }
  catch (_: HttpRequestTimeoutException) { throw HttpException(0, kind = HttpFailureKind.TIMEOUT) }
  catch (_: ConnectTimeoutException) { throw HttpException(0, kind = HttpFailureKind.TIMEOUT) }
  catch (_: SocketTimeoutException) { throw HttpException(0, kind = HttpFailureKind.TIMEOUT) }
  catch (_: IllegalArgumentException) { throw HttpException(0, kind = HttpFailureKind.INVALID_REQUEST) }
  catch (e: Exception) {
    AppLog.warn("api", "transport failed (${e::class.simpleName})")
    throw HttpException(0, kind = HttpFailureKind.NETWORK)
  }

  private companion object {
    const val USER_AGENT = "Berill/0.6 (personal app)"
  }
}
