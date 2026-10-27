package dev.akkraizen.berill.updates

import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.releaseDownloads(releases: Releases) {
  get("/download/latest/{platform}") {
    val latest = withContext(Dispatchers.IO) { releases.latest() }
    val path = when (call.parameters["platform"]) {
      "windows" -> latest?.windows
      "macos" -> latest?.macos
      "android" -> latest?.android
      else -> null
    }
    call.response.header(HttpHeaders.CacheControl, "no-store")
    if (path == null) call.respond(HttpStatusCode.NotFound) else call.respondRedirect(path, permanent = false)
  }
  get("/download/{file}") {
    val file = call.parameters["file"]?.let(releases::file)
    if (file == null) {
      call.respond(HttpStatusCode.NotFound)
      return@get
    }
    call.response.header(
      HttpHeaders.ContentDisposition,
      ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.name).toString()
    )
    call.respondFile(file)
  }
}

fun Route.latestReleaseApi(releases: Releases) {
  get("/api/release") {
    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
    val latest = withContext(Dispatchers.IO) { releases.latest() }
    if (latest == null) call.respond(HttpStatusCode.NoContent) else call.respond(latest)
  }
}
