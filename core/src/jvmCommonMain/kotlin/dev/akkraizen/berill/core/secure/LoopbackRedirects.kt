package dev.akkraizen.berill.core.secure

import dev.akkraizen.berill.api.OAuthRedirects
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class LoopbackRedirects(
  private val timeoutMillis: Long = 5 * 60_000L,
  private val openContext: CoroutineContext = Dispatchers.Main.immediate
) : OAuthRedirects {
  override suspend fun receive(open: (redirectUri: String) -> Unit): Map<String, String> =
    withContext(Dispatchers.IO) {
      ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
        server.soTimeout = 500
        withContext(openContext) { open("http://127.0.0.1:${server.localPort}") }
        await(server, System.currentTimeMillis() + timeoutMillis)
      }
    }

  private suspend fun await(server: ServerSocket, deadline: Long): Map<String, String> {
    while (true) {
      coroutineContext.ensureActive()
      if (System.currentTimeMillis() > deadline) throw IOException("Sign-in timed out")
      val socket = try {
        server.accept()
      } catch (_: SocketTimeoutException) {
        continue
      }
      val params = socket.use { answer(it) }
      if (params.isNotEmpty()) return params
    }
  }

  private fun answer(s: Socket): Map<String, String> {
    s.soTimeout = 5_000
    val line = s.getInputStream().bufferedReader().readLine().orEmpty()
    val target = line.split(' ').getOrNull(1).orEmpty()
    val params = parse(target.substringAfter('?', ""))
    val body = if (params.isEmpty()) "" else PAGE
    val status = if (params.isEmpty()) "404 Not Found" else "200 OK"
    val bytes = body.toByteArray(Charsets.UTF_8)
    val head = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
      "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
    s.getOutputStream().apply {
      write(head.toByteArray(Charsets.US_ASCII))
      write(bytes)
      flush()
    }
    return params
  }

  private fun parse(query: String): Map<String, String> =
    query.split('&')
      .filter { it.isNotEmpty() }
      .associate {
        val key = URLDecoder.decode(it.substringBefore('='), "UTF-8")
        val value = URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        key to value
      }

  private companion object {
    const val PAGE = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Berill</title></head>" +
      "<body style=\"font-family:sans-serif;background:#111614;color:#e1e3df;display:flex;" +
      "align-items:center;justify-content:center;height:100vh;margin:0\">" +
      "<div style=\"text-align:center\"><h2>Berill</h2><p>Можно вернуться в приложение.</p>" +
      "<p style=\"opacity:.7\">You can return to the app.</p></div></body></html>"
  }
}
