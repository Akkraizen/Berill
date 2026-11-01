package dev.akkraizen.berill.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.nio.file.Files
import kotlin.test.*

class BlobRoutesTest {
  @Test fun blobsAreAuthenticatedIsolatedAndBounded() = testApplication {
    val directory = Files.createTempDirectory("berill-blobs").toFile()
    try {
      application {
        install(Authentication) { bearer("test") { authenticate { if (it.token in setOf("one", "two")) UserIdPrincipal(it.token) else null } } }
        routing { authenticate("test") { blobRoutes(directory, maxBytes = 8) } }
      }
      val hash = "a".repeat(64)
      assertEquals(HttpStatusCode.Unauthorized, client.get("/api/blobs/$hash").status)
      assertEquals(HttpStatusCode.NoContent, client.put("/api/blobs/$hash") { bearerAuth("one"); setBody("photo".encodeToByteArray()) }.status)
      assertEquals("photo", client.get("/api/blobs/$hash") { bearerAuth("one") }.bodyAsText())
      assertEquals(HttpStatusCode.NotFound, client.get("/api/blobs/$hash") { bearerAuth("two") }.status)
      assertEquals(HttpStatusCode.BadRequest, client.put("/api/blobs/not-a-hash") { bearerAuth("one"); setBody(byteArrayOf()) }.status)
      assertEquals(HttpStatusCode.PayloadTooLarge, client.put("/api/blobs/$hash") { bearerAuth("one"); setBody("too large payload".encodeToByteArray()) }.status)
      assertEquals("photo", client.get("/api/blobs/$hash") { bearerAuth("one") }.bodyAsText())
    } finally { directory.deleteRecursively() }
  }
}
