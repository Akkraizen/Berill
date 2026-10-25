package dev.akkraizen.berill.core.sync

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal val syncJson = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
}

internal fun createHttpClient(): HttpClient =
  HttpClient {
    install(ContentNegotiation) { json(syncJson) }
    install(HttpTimeout) { requestTimeoutMillis = 30_000 }
  }
