package dev.akkraizen.berill.server

import io.ktor.utils.io.readAvailable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.blobRoutes(root: File = File(System.getenv("ATTACHMENTS_PATH") ?: "/app/attachments"), maxBytes: Int = 21 * 1024 * 1024) {
  blobRoutes(FileBlobStore(root), maxBytes)
}

fun Route.blobRoutes(store: BlobStore, maxBytes: Int = 21 * 1024 * 1024) {
  fun blob(user: String, hash: String): String {
    require(hash.matches(Regex("[0-9a-f]{64}")))
    val namespace = MessageDigest.getInstance("SHA-256").digest(user.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    return "$namespace/$hash"
  }
  get("/api/blobs/{hash}") {
    val hash = call.parameters["hash"].orEmpty()
    if (!hash.matches(Regex("[0-9a-f]{64}"))) { call.respond(HttpStatusCode.BadRequest); return@get }
    val bytes = withContext(Dispatchers.IO) { store.get(blob(call.principal<UserIdPrincipal>()!!.name, hash)) }
    if (bytes != null) call.respondBytes(bytes, ContentType.Application.OctetStream) else call.respond(HttpStatusCode.NotFound)
  }
  put("/api/blobs/{hash}") {
    val hash = call.parameters["hash"].orEmpty()
    if (!hash.matches(Regex("[0-9a-f]{64}"))) { call.respond(HttpStatusCode.BadRequest); return@put }
    val channel = call.receiveChannel()
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(65536)
    while (true) {
      val count = channel.readAvailable(buffer, 0, buffer.size)
      if (count < 0) break
      if (out.size() + count > maxBytes) { call.respond(HttpStatusCode.PayloadTooLarge); return@put }
      out.write(buffer, 0, count)
    }
    withContext(Dispatchers.IO) {
      store.put(blob(call.principal<UserIdPrincipal>()!!.name, hash), out.toByteArray())
    }
    call.respond(HttpStatusCode.NoContent)
  }
}
