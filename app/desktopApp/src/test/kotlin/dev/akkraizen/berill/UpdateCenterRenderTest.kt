package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.ui.shell.BerillShell
import dev.akkraizen.berill.core.update.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class UpdateCenterRenderTest {
  private suspend fun render(scene: ImageComposeScene, file: String) {
    repeat(12) { delay(40); scene.render(System.nanoTime()).close() }
    val image = scene.render(System.nanoTime())
    try { val data = image.encodeToData()!!; try { File(System.getProperty("berill.uiEvidenceDir"), file).apply { parentFile.mkdirs(); writeBytes(data.bytes) } } finally { data.close() } } finally { image.close() }
  }
  private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
  private fun text(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

  @Test fun updateCenterShowsReadyPackageWithoutOpeningBrowser() = runBlocking(Dispatchers.Main) {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val metadata = """{"version":"99.0.0","windows":"/download/Berill-99.0.0-windows.exe","artifacts":{"windows":{"path":"/download/Berill-99.0.0-windows.exe","sha256":"${"a".repeat(64)}","size":10}},"notes":"A verified test update"}""".encodeToByteArray()
    server.createContext("/api/release") { call -> call.responseHeaders.set("Content-Type", "application/json"); call.sendResponseHeaders(200, metadata.size.toLong()); call.responseBody.use { it.write(metadata) }; call.close() }
    server.start()
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val installer = object : UpdateInstaller {
      override val available = true
      override val platform = "windows"
      override suspend fun download(update: AppUpdate, progress: (Long, Long?) -> Unit): String { progress(10, 10); return "test-package" }
      override suspend fun validate(path: String, update: AppUpdate) {}
      override suspend fun install(path: String, update: AppUpdate): InstallResult = error("Must not install packages from UI tests")
      override suspend fun remove(path: String) {}
    }
    val core = BerillCore(driver, emptyList(), scope, updateInstaller = installer)
    core.finishOnboarding(); core.appearance.setLanguage("ru")
    core.updates.setServerUrl("http://127.0.0.1:${server.address.port}")
    core.updates.check()
    val update = (core.updates.state.value as UpdateCheck.Available).update
    val installLabel = if (System.getProperty("os.name").startsWith("Windows")) "core.update.restartInstall" else "core.update.install"
    val scene = ImageComposeScene(360, 900) { BerillShell(core) }
    try {
      render(scene, "update-center-available.png")
      assertTrue(text(scene).contains(I18n.text("core.update.downloadInApp")))
      core.updates.download(update)
      withTimeout(5000) { core.updates.transfer.first { it is UpdateTransfer.Ready } }
      render(scene, "update-center-ready.png")
      assertTrue(text(scene).contains(I18n.text(installLabel)))
      assertTrue(text(scene).contains(I18n.text("core.update.ready")))
      assertTrue(text(scene).contains(I18n.text("core.update.discard")))
      core.nav.openSettings("updates")
      render(scene, "update-center-settings.png")
      assertTrue(text(scene).contains(I18n.text(installLabel)))
      assertTrue(text(scene).contains(I18n.text("core.update.server")))
    } finally { scene.close(); scope.cancel(); driver.close(); server.stop(0) }
  }
}
