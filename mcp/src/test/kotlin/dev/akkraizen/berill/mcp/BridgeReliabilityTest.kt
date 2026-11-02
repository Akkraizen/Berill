package dev.akkraizen.berill.mcp

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.StringReader
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class BridgeReliabilityTest {
  @Test fun ambiguousMutationFailureIsNotReplayedAndNegotiatedProtocolIsForwarded() {
    val mutations = AtomicInteger()
    val protocolHeaders = mutableListOf<String?>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/mcp") { exchange ->
      try {
        if (exchange.requestMethod == "DELETE" || exchange.requestMethod == "GET") { exchange.sendResponseHeaders(405, -1); return@createContext }
        val body = exchange.requestBody.bufferedReader().readText()
        protocolHeaders += exchange.requestHeaders.getFirst("MCP-Protocol-Version")
        when {
          body.contains("notifications/initialized") -> exchange.sendResponseHeaders(202, -1)
          body.contains("initialize") -> {
            val response = """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25","capabilities":{},"serverInfo":{"name":"generic","version":"1"}}}""".toByteArray()
            exchange.responseHeaders.add("Mcp-Session-Id", "test-session")
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong()); exchange.responseBody.write(response)
          }
          else -> {
            mutations.incrementAndGet()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, 1000)
            exchange.responseBody.write("{".toByteArray())
          }
        }
      } finally { exchange.close() }
    }
    server.start()
    try {
      val input = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create_record","arguments":{}}}
"""
      val output = StringWriter()
      McpBridge({ BridgeTarget("http://127.0.0.1:${server.address.port}/mcp", "token") }, StringReader(input).buffered(), output).run()
      assertEquals(1, mutations.get())
      assertEquals("2025-11-25", protocolHeaders.last())
      assertTrue("outcome may be unknown" in output.toString())
    } finally { server.stop(0) }
  }

  @Test fun slowCallsDoNotBlockOtherRequests() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.executor = java.util.concurrent.Executors.newCachedThreadPool()
    server.createContext("/mcp") { exchange ->
      try {
        if (exchange.requestMethod != "POST") { exchange.sendResponseHeaders(405, -1); return@createContext }
        val body = exchange.requestBody.bufferedReader().readText()
        if (body.contains("notifications/initialized")) { exchange.sendResponseHeaders(202, -1); return@createContext }
        val id = Regex("\"id\":(\\d+)").find(body)!!.groupValues[1]
        if (body.contains("tools/call")) Thread.sleep(1500)
        val result = if (body.contains("initialize")) """{"protocolVersion":"2025-11-25","capabilities":{},"serverInfo":{"name":"generic","version":"1"}}""" else "{}"
        val response = """{"jsonrpc":"2.0","id":$id,"result":$result}""".toByteArray()
        exchange.responseHeaders.add("Mcp-Session-Id", "s")
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, response.size.toLong()); exchange.responseBody.write(response)
      } finally { exchange.close() }
    }
    server.start()
    try {
      val input = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search","arguments":{}}}
{"jsonrpc":"2.0","id":3,"method":"ping"}
"""
      val output = StringWriter()
      McpBridge({ BridgeTarget("http://127.0.0.1:${server.address.port}/mcp", "token") }, StringReader(input).buffered(), output).run()
      val ids = output.toString().lines().filter { it.isNotBlank() }.map { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1] }
      assertEquals(listOf("1", "3", "2"), ids)
    } finally { server.stop(0) }
  }

  @Test fun invalidLinesGetAParseError() {
    val output = StringWriter()
    McpBridge({ null }, StringReader("not json\n").buffered(), output).run()
    assertTrue("-32700" in output.toString(), output.toString())
  }
}
