package dev.akkraizen.berill.server

import dev.akkraizen.berill.protocol.SyncRequest
import dev.akkraizen.berill.server.tokens.TokenRepository
import dev.akkraizen.berill.server.tokens.runTokenCli
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
  if (args.firstOrNull() == "token") {
    kotlin.system.exitProcess(status = runTokenCli(args.drop(1)))
  }

  val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
  embeddedServer(factory = Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
  val db = ServerDatabase.fromEnv()
  val repository = SyncRepository(db)
  val tokenStore = TokenRepository(db)
  val status = StatusRepository(db)
  val tokens: Map<String, String> = parseTokens()

  if (tokens.isEmpty() && !tokenStore.hasActiveTokens()) {
    log.warn("No tokens are configured; sync requests will be rejected. Create a token with: token create <user>")
  }

  install(CallLogging)
  install(ContentNegotiation) {
    json(
      Json {
        ignoreUnknownKeys = true
        explicitNulls = false
      }
    )
  }
  install(StatusPages) {
    exception<Throwable> { call, cause ->
      call.application.log.error("Request failed", cause)
      call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "internal error")))
    }
  }
  install(Authentication) {
    bearer("api") {
      authenticate { credential ->
        val user = tokens[credential.token] ?: withContext(Dispatchers.IO) {
          tokenStore.authenticate(credential.token)
        }
        user?.let(::UserIdPrincipal)
      }
    }
  }

  routing {
    get("/health") { call.respondText("ok") }
    get("/status") { call.respondText(STATUS_PAGE, ContentType.Text.Html) }
    authenticate("api") {
      post("/api/sync") {
        val user = call.principal<UserIdPrincipal>()!!.name
        val request = call.receive<SyncRequest>()
        val response = repository.sync(user, request)
        withContext(Dispatchers.IO) { status.touch(user, request.deviceId, request.client) }
        call.respond(response)
      }
      get("/api/status") {
        val user = call.principal<UserIdPrincipal>()!!.name
        call.respond(withContext(Dispatchers.IO) { status.status(user) })
      }
    }
  }
}

private fun parseTokens(): Map<String, String> {
  val env = System.getenv("HUB_TOKENS") ?: ""
  val res = env
    .split(',')
    .mapNotNull { pair ->
      pair.split(':', limit = 2)
        .takeIf { it.size == 2 && it[1].length >= 16 }
    }
    .associate { (user, token) -> token.trim() to user.trim() }

  return res
}
