package dev.akkraizen.berill.core.data

import dev.akkraizen.berill.api.HttpAccess
import dev.akkraizen.berill.api.HttpException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class KtorHttpAccess : HttpAccess {
  private val client = HttpClient {
    install(HttpTimeout) {
      requestTimeoutMillis = 20_000
      connectTimeoutMillis = 10_000
    }
    expectSuccess = false
  }

  override suspend fun getJson(url: String, headers: Map<String, String>): JsonElement {
    val extra = headers
    val response = client.get(url) {
      header(HttpHeaders.UserAgent, USER_AGENT)
      header(HttpHeaders.Accept, "application/json")
      extra.forEach { (name, value) -> header(name, value) }
    }
    val status = response.status.value
    if (status >= 400) throw HttpException(status)
    return Json.parseToJsonElement(response.bodyAsText())
  }

  private companion object {
    const val USER_AGENT = "Berill/0.6 (personal app)"
  }
}
