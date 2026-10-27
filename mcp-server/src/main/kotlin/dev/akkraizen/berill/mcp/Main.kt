package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.sync.E2eState
import dev.akkraizen.berill.core.sync.SyncState
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

private const val USAGE = """Usage:
  berill-mcp [serve] [--data DIR] [--read-only]
  berill-mcp login --server URL [--token TOKEN] [--data DIR]
  berill-mcp status [--data DIR]
  berill-mcp logout [--data DIR]

The token and the encryption password can also come from BERILL_TOKEN and BERILL_E2E_PASSWORD.
Data is kept in ~/.berill-mcp unless --data is given."""

internal class Arguments(args: Array<String>) {
  val command: String
  private val options = mutableMapOf<String, String>()
  private val flags = mutableSetOf<String>()

  init {
    val rest = args.toMutableList()
    command = rest.firstOrNull()?.takeUnless { it.startsWith("--") }?.also { rest.removeAt(0) } ?: "serve"
    var i = 0
    while (i < rest.size) {
      val arg = rest[i]
      require(arg.startsWith("--")) { "Unexpected argument `$arg`" }
      val name = arg.removePrefix("--")
      if (name == "read-only" || name == "help") flags += name
      else {
        require(i + 1 < rest.size) { "Missing value for $arg" }
        options[name] = rest[i + 1]
        i++
      }
      i++
    }
  }

  fun option(name: String): String? = options[name]

  fun flag(name: String): Boolean = name in flags
}

fun main(args: Array<String>) {
  val arguments = runCatching { Arguments(args) }.getOrElse {
    System.err.println("${it.message}\n\n$USAGE")
    exitProcess(2)
  }
  if (arguments.flag("help") || arguments.command == "help") {
    println(USAGE)
    return
  }
  val dir = arguments.option("data")?.let(::File) ?: defaultDataDirectory()
  val code = when (arguments.command) {
    "serve" -> serve(dir, arguments.flag("read-only"))
    "login" -> login(dir, arguments)
    "status" -> status(dir)
    "logout" -> logout(dir)
    else -> {
      System.err.println("Unknown command `${arguments.command}`\n\n$USAGE")
      2
    }
  }
  exitProcess(code)
}

private fun serve(dir: File, readOnly: Boolean): Int = runBlocking {
  val protocolOut = System.out
  System.setOut(System.err)
  val core = openCore(dir)
  if (core.sync.serverUrl() != null) core.sync.start()
  val server = createBerillServer(BerillTools(core), readOnly)
  val transport = StdioServerTransport(System.`in`.asSource().buffered(), protocolOut.asSink().buffered())
  val closed = CompletableDeferred<Unit>()
  val session = server.createSession(transport)
  session.onClose { closed.complete(Unit) }
  closed.await()
  core.sync.syncNow()
  0
}

private fun login(dir: File, arguments: Arguments): Int = runBlocking {
  val url = arguments.option("server") ?: run {
    System.err.println("Pass the sync server address with --server, e.g. https://sync.berill.space")
    return@runBlocking 2
  }
  val token = arguments.option("token") ?: System.getenv("BERILL_TOKEN") ?: prompt("Access token: ", secret = true)
  if (token.isNullOrBlank()) {
    System.err.println("An access token is required. Create one on the server: token create <user> --name mcp")
    return@runBlocking 2
  }
  val core = openCore(dir)
  core.sync.configure(url, token)
  val synced = awaitSync(core)
  core.encryption.refresh()
  if (!synced && core.encryption.state.value != E2eState.Locked) {
    System.err.println("Sync failed: ${core.sync.diagnostics().lastError ?: "unknown error"}")
    return@runBlocking 1
  }
  if (core.encryption.state.value == E2eState.Locked) {
    val password = System.getenv("BERILL_E2E_PASSWORD") ?: prompt("Encryption password: ", secret = true)
    if (password.isNullOrEmpty() || !core.encryption.unlock(password)) {
      System.err.println("Wrong encryption password. Encrypted records stay unreadable until you log in again.")
      return@runBlocking 1
    }
    core.sync.syncFromStart()
    if (core.sync.state.value is SyncState.Failed) {
      System.err.println("Sync failed: ${core.sync.diagnostics().lastError ?: "unknown error"}")
      return@runBlocking 1
    }
  }
  println(describe(core))
  0
}

private fun status(dir: File): Int {
  println(describe(openCore(dir)))
  return 0
}

private fun logout(dir: File): Int = runBlocking {
  val core = openCore(dir)
  core.encryption.forgetKey()
  core.sync.disconnect()
  println("Signed out. Local data in ${dir.absolutePath} was kept; delete the folder to remove it.")
  0
}

private suspend fun awaitSync(core: BerillCore): Boolean {
  val state = withTimeoutOrNull(120_000) {
    while (true) {
      when (val s = core.sync.state.value) {
        is SyncState.Done, is SyncState.Failed -> return@withTimeoutOrNull s
        else -> delay(200)
      }
    }
    @Suppress("UNREACHABLE_CODE") null
  }
  if (state == null) System.err.println("Sync did not finish within 2 minutes.")
  return state is SyncState.Done
}

private fun describe(core: BerillCore): String {
  val d = core.sync.diagnostics()
  return buildString {
    appendLine("Server: ${d.serverUrl ?: "not configured"}")
    d.user?.let { appendLine("User: $it") }
    appendLine("Records: ${d.totalRecords}")
    appendLine("Encryption: ${d.encryption.name.lowercase()}")
    d.lastError?.let { appendLine("Last error: $it") }
    append("Device id: ${d.deviceId}")
  }
}

private fun prompt(text: String, secret: Boolean): String? {
  val console = System.console()
  if (console != null) return if (secret) console.readPassword(text)?.let(::String) else console.readLine(text)
  System.err.print(text)
  return readlnOrNull()
}
