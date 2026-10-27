package dev.akkraizen.berill.updates

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateRoutesTest {
  private fun releasesWith(vararg names: String): Releases {
    val dir = Files.createTempDirectory("releases").toFile()
    names.forEach { File(dir, it).writeText("file:$it") }
    return Releases(dir)
  }

  @Test
  fun latestReleaseIsPublic() = testApplication {
    application { updatesModule(releasesWith("Berill-1.2.0-android.apk", "Berill-1.2.0-Setup.exe")) }

    val response = client.get("/api/release")

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.bodyAsText()
    assertTrue("\"version\":\"1.2.0\"" in body, body)
    assertTrue("/download/Berill-1.2.0-android.apk" in body, body)
  }

  @Test
  fun noReleasesMeansNoContent() = testApplication {
    application { updatesModule(releasesWith()) }

    assertEquals(HttpStatusCode.NoContent, client.get("/api/release").status)
  }

  @Test
  fun downloadsOnlyReleaseFiles() = testApplication {
    application { updatesModule(releasesWith("Berill-1.2.0-android.apk", "notes.txt")) }

    val ok = client.get("/download/Berill-1.2.0-android.apk")
    assertEquals(HttpStatusCode.OK, ok.status)
    assertEquals("file:Berill-1.2.0-android.apk", ok.bodyAsText())
    assertEquals(HttpStatusCode.NotFound, client.get("/download/notes.txt").status)
    assertEquals(HttpStatusCode.NotFound, client.get("/download/..%2Fnotes.txt").status)
  }

  @Test
  fun latestDownloadRedirectsToNewestFileOfPlatform() = testApplication {
    application {
      updatesModule(
        releasesWith("Berill-1.1.0-Setup.exe", "Berill-1.2.0-Setup.exe", "Berill-1.2.0-macos.dmg", "Berill-1.2.0-android.apk")
      )
    }
    val noRedirects = createClient { followRedirects = false }

    val windows = noRedirects.get("/download/latest/windows")
    assertEquals(HttpStatusCode.Found, windows.status)
    assertEquals("/download/Berill-1.2.0-Setup.exe", windows.headers[HttpHeaders.Location])
    assertEquals("/download/Berill-1.2.0-macos.dmg", noRedirects.get("/download/latest/macos").headers[HttpHeaders.Location])
    assertEquals("/download/Berill-1.2.0-android.apk", noRedirects.get("/download/latest/android").headers[HttpHeaders.Location])
    assertEquals(HttpStatusCode.NotFound, noRedirects.get("/download/latest/linux").status)
  }

  @Test
  fun latestDownloadIsMissingWhenPlatformHasNoFile() = testApplication {
    application { updatesModule(releasesWith("Berill-1.2.0-android.apk")) }
    val noRedirects = createClient { followRedirects = false }

    assertEquals(HttpStatusCode.NotFound, noRedirects.get("/download/latest/macos").status)
  }

  @Test
  fun latestReleaseCanBeReadFromBrowsers() = testApplication {
    application { updatesModule(releasesWith("Berill-1.2.0-android.apk")) }

    assertEquals("*", client.get("/api/release").headers[HttpHeaders.AccessControlAllowOrigin])
  }

  @Test
  fun healthCheck() = testApplication {
    application { updatesModule(releasesWith()) }

    assertEquals("ok", client.get("/health").bodyAsText())
  }
}
