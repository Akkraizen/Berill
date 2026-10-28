package dev.akkraizen.berill.core

import dev.akkraizen.berill.core.secure.LoopbackRedirects
import java.net.HttpURLConnection
import java.net.URI
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LoopbackRedirectsTest {
  private fun get(url: String): Pair<Int, String> {
    val c = URI(url).toURL().openConnection() as HttpURLConnection
    val code = c.responseCode
    val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
    return code to body
  }

  @Test
  fun returnsQueryOfTheFirstRedirectWithParameters(): Unit = runBlocking {
    var favicon = 0
    var page = ""
    val params = LoopbackRedirects(timeoutMillis = 10_000, openContext = Dispatchers.Unconfined).receive { redirect ->
      assertTrue(redirect.startsWith("http://127.0.0.1:"))
      thread {
        favicon = get("$redirect/favicon.ico").first
        page = get("$redirect/?code=4%2F0Ab&state=xyz").second
      }
    }
    assertEquals(mapOf("code" to "4/0Ab", "state" to "xyz"), params)
    Thread.sleep(200)
    assertEquals(404, favicon)
    assertTrue(page.contains("Berill"))
  }
}
