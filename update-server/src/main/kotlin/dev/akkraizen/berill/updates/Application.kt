package dev.akkraizen.berill.updates

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
  val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
  embeddedServer(factory = Netty, port = port) { updatesModule(Releases.fromEnv()) }.start(wait = true)
}

fun Application.updatesModule(releases: Releases) {
  log.info("Berill update server ${UpdatesVersion.value}, releases from ${releases.directory.absolutePath}")
  install(CallLogging)
  install(ContentNegotiation) {
    json(
      Json {
        explicitNulls = false
      }
    )
  }
  install(StatusPages) {
    exception<Throwable> { call, cause ->
      call.application.log.error("Request failed", cause)
      call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "internal error"))
    }
  }
  routing {
    get("/health") { call.respondText("ok") }
    latestReleaseApi(releases)
    releaseDownloads(releases)
  }
}

object UpdatesVersion {
  val value: String by lazy {
    UpdatesVersion::class.java.getResourceAsStream("/berill-updates-version.txt")
      ?.use { it.readBytes().decodeToString().trim() }
      ?.takeIf { it.isNotEmpty() }
      ?: "dev"
  }
}
