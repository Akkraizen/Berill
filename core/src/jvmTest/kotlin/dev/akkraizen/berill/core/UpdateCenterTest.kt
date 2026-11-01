package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sun.net.httpserver.HttpServer
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.update.*
import dev.akkraizen.berill.protocol.HybridClock
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

class UpdateCenterTest {
  private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
  private class DownloadOnly(dir: File) : FileUpdateInstaller(dir) {
    override val platform = "windows"
    override suspend fun install(path: String, update: AppUpdate): InstallResult = error("Must not launch installers in tests")
  }
  @Test fun verifiedDownloadIsRestoredAfterRestartAndDiscardedExplicitly() = runBlocking {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val store = LocalStore(BerillDatabase(driver), HybridClock("restore-update") { 0 })
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val installer = FakeInstaller()
    try {
      val first = AppUpdates(store, installer, scope)
      first.setServerUrl("https://example.com")
      first.download(AppUpdate("9.0.0", "https://example.com/download/Berill-9.0.0-windows.exe", "a".repeat(64), 10))
      withTimeout(5000) { first.transfer.first { it is UpdateTransfer.Ready } }
      val headless = AppUpdates(store, NoUpdateInstaller, scope)
      assertEquals(UpdateTransfer.Idle, headless.transfer.value)
      val restored = AppUpdates(store, installer, scope)
      assertIs<UpdateTransfer.Ready>(restored.transfer.value)
      assertEquals("9.0.0", (restored.state.value as UpdateCheck.Available).update.version)
      restored.cancel()
      assertEquals(UpdateTransfer.Idle, AppUpdates(store, installer, scope).transfer.value)
    } finally { scope.cancel(); driver.close() }
  }

  @Test fun windowsHelperParsesAndWaitsForOnlyTheCurrentProcess() {
    if (!System.getProperty("os.name").startsWith("Windows")) return
    val directory = Files.createTempDirectory("update-helper").toFile()
    try {
      val installer = DesktopUpdateInstaller(directory)
      val script = installer.windowsInstallScript("C:\\Users\\O'Brien\\package-update.exe", "C:\\Program Files\\Berill\\Berill.exe", 12345L, "a".repeat(64))
      assertTrue(script.contains("O''Brien"))
      assertTrue(script.indexOf("Wait-Process -Id 12345") < script.indexOf("Start-Process -FilePath"))
      assertTrue(script.contains("Get-FileHash"))
      assertFalse(script.contains("Stop-Process"))
      val source = File(directory, "helper.ps1").apply { writeText(script) }
      val checker = """
        ${'$'}tokens = ${'$'}null; ${'$'}errors = ${'$'}null
        [System.Management.Automation.Language.Parser]::ParseFile(${'$'}env:BERILL_TEST_SCRIPT, [ref]${'$'}tokens, [ref]${'$'}errors) | Out-Null
        if (${'$'}errors.Count) { ${'$'}errors | Out-String | Write-Output; exit 1 }
      """.trimIndent()
      val builder = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", checker).redirectErrorStream(true)
      builder.environment()["BERILL_TEST_SCRIPT"] = source.absolutePath
      val process = builder.start()
      assertTrue(process.waitFor(15, TimeUnit.SECONDS))
      assertEquals(0, process.exitValue(), process.inputStream.bufferedReader().readText())
    } finally { directory.deleteRecursively() }
  }

  @Test fun downloadVerifiesBytesAndRejectsTamperedOrUntrustedPackages() = runBlocking {
    val directory = Files.createTempDirectory("update-download").toFile()
    val bytes = "test installer".encodeToByteArray()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { call -> call.sendResponseHeaders(200, bytes.size.toLong()); call.responseBody.use { it.write(bytes) }; call.close() }
    server.start()
    try {
      val installer = DownloadOnly(directory)
      val update = AppUpdate("9.0.0", "http://127.0.0.1:${server.address.port}/download/Berill-9.0.0-windows.exe", hash(bytes), bytes.size.toLong())
      val progress = mutableListOf<Long>()
      val path = installer.download(update) { n, _ -> progress += n }
      assertContentEquals(bytes, File(path).readBytes())
      assertEquals(bytes.size.toLong(), progress.last())
      installer.validate(path, update)
      File(path).writeBytes(ByteArray(bytes.size))
      assertEquals(UpdateFailure.INTEGRITY, assertFailsWith<UpdateException> { installer.validate(path, update) }.reason)
      installer.remove(path)
      assertEquals(UpdateFailure.INTEGRITY, assertFailsWith<UpdateException> { installer.download(update.copy(sha256 = "0".repeat(64))) { _, _ -> } }.reason)
      assertTrue(directory.listFiles().orEmpty().isEmpty(), "Failed downloads must not leave partial packages")
      assertEquals(UpdateFailure.INSECURE, assertFailsWith<UpdateException> { installer.download(update.copy(url = "http://example.com/Berill-9.0.0-windows.exe")) { _, _ -> } }.reason)
    } finally { server.stop(0); directory.deleteRecursively() }
  }
  @Test fun cancelledStreamRemovesItsPartialFile() = runBlocking {
    val directory = Files.createTempDirectory("update-cancel").toFile()
    val release = CountDownLatch(1)
    val progress = CompletableDeferred<Unit>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { call ->
      try { call.sendResponseHeaders(200, 1024); call.responseBody.write(1); call.responseBody.flush(); release.await(5, TimeUnit.SECONDS) }
      finally { call.close() }
    }
    server.start()
    val job = async(Dispatchers.Default) {
      DownloadOnly(directory).download(AppUpdate("9.0.0", "http://127.0.0.1:${server.address.port}/Berill-9.0.0-windows.exe", "0".repeat(64), 1024)) { _, _ -> progress.complete(Unit) }
    }
    try {
      withTimeout(5000) { progress.await() }
      job.cancelAndJoin()
      assertTrue(directory.listFiles().orEmpty().isEmpty())
    } finally { release.countDown(); job.cancelAndJoin(); server.stop(0); directory.deleteRecursively() }
  }

  private class FakeInstaller : UpdateInstaller {
    override val available = true
    override val platform = "windows"
    var first = CompletableDeferred<Unit>()
    var calls = 0
    var blockFirst = false
    var result = InstallResult.PERMISSION_REQUIRED
    var flushed = false
    override suspend fun download(update: AppUpdate, progress: (Long, Long?) -> Unit): String {
      calls++
      if (blockFirst && calls == 1) {
        first.complete(Unit)
        try { awaitCancellation() } finally { withContext(NonCancellable) { delay(100) } }
      }
      progress(10, 10)
      return "test-package"
    }
    override suspend fun validate(path: String, update: AppUpdate) {}
    override suspend fun install(path: String, update: AppUpdate): InstallResult { check(flushed); return result }
    override suspend fun remove(path: String) {}
  }
  @Test fun cancellationCannotOverwriteNewDownloadAndInstallFlushesEditor() = runBlocking {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val installer = FakeInstaller().apply { blockFirst = true }
    val updates = AppUpdates(LocalStore(BerillDatabase(driver), HybridClock("update-test") { 0 }), installer, scope) { installer.flushed = true }
    val update = AppUpdate("9.0.0", "https://example.com/Berill-9.0.0-windows.exe", "a".repeat(64), 10)
    try {
      updates.download(update)
      withTimeout(5000) { installer.first.await() }
      updates.cancel()
      updates.download(update)
      withTimeout(5000) { updates.transfer.first { it is UpdateTransfer.Ready } }
      delay(200)
      assertIs<UpdateTransfer.Ready>(updates.transfer.value)
      updates.install()
      withTimeout(5000) { updates.transfer.first { it is UpdateTransfer.Ready && it.hint == InstallResult.PERMISSION_REQUIRED } }
      assertTrue(installer.flushed)
      assertFalse(updates.exitRequested.value)
      installer.result = InstallResult.EXIT_REQUIRED
      delay(20)
      updates.install()
      withTimeout(5000) { updates.exitRequested.first { it } }
      Unit
    } finally { scope.cancel(); driver.close() }
  }
  @Test fun legacyManifestDoesNotAllowUnverifiedInstallation() = runBlocking {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val installer = FakeInstaller()
    val updates = AppUpdates(LocalStore(BerillDatabase(driver), HybridClock("legacy-update") { 0 }), installer, scope)
    try {
      updates.download(AppUpdate("9.0.0", "https://example.com/Berill-9.0.0-windows.exe"))
      val failed = withTimeout(5000) { updates.transfer.first { it is UpdateTransfer.Failed } } as UpdateTransfer.Failed
      assertEquals(UpdateFailure.SERVER_TOO_OLD, failed.reason)
      assertEquals(0, installer.calls)
    } finally { scope.cancel(); driver.close() }
  }

  private class Signed(dir: File, override val publicKey: String) : FileUpdateInstaller(dir) {
    override val platform = "windows"
    override suspend fun install(path: String, update: AppUpdate): InstallResult = error("Must not launch installers in tests")
  }

  @Test fun publisherSignatureIsRequiredWhenTheBuildHasAKey() = runBlocking {
    val keys = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val publicKey = java.util.Base64.getEncoder().encodeToString(keys.public.encoded)
    fun sign(name: String, sha: String, size: Long, key: java.security.PrivateKey = keys.private) = java.util.Base64.getEncoder().encodeToString(
      java.security.Signature.getInstance("SHA256withECDSA").run { initSign(key); update(dev.akkraizen.berill.protocol.UpdateSignature.message(name, sha, size).encodeToByteArray()); sign() })
    val bytes = "signed installer".encodeToByteArray()
    val name = "Berill-9.0.0-windows.exe"
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { call -> call.sendResponseHeaders(200, bytes.size.toLong()); call.responseBody.use { it.write(bytes) } }
    server.start()
    val directory = Files.createTempDirectory("signed-update").toFile()
    try {
      val url = "http://127.0.0.1:${server.address.port}/download/$name"
      val good = AppUpdate("9.0.0", url, hash(bytes), bytes.size.toLong(), signature = sign(name, hash(bytes), bytes.size.toLong()))
      val installer = Signed(directory, publicKey)
      val path = installer.download(good) { _, _ -> }
      installer.validate(path, good)

      val other = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
      for (bad in listOf(
        good.copy(signature = null),
        good.copy(signature = "not-base64!"),
        good.copy(signature = sign(name, hash(bytes), bytes.size.toLong(), other.private)),
        good.copy(signature = sign("Berill-9.0.0-android.apk", hash(bytes), bytes.size.toLong())),
      )) {
        assertEquals(UpdateFailure.SIGNATURE, assertFailsWith<UpdateException> { installer.download(bad) { _, _ -> } }.reason)
        assertEquals(UpdateFailure.SIGNATURE, assertFailsWith<UpdateException> { installer.validate(path, bad) }.reason)
      }
      assertEquals(listOf(java.io.File(path).name), directory.listFiles()!!.map { it.name }, "rejected packages are never downloaded")
      // Builds without a key keep the SHA-256-only behaviour.
      Signed(directory, "").validate(path, good.copy(signature = null))
    } finally { server.stop(0); directory.deleteRecursively() }
  }
}
