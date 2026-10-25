package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.hostMatches
import dev.akkraizen.berill.api.ui.navigation.isSafeExternalLink
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.secure.MemoryVault
import dev.akkraizen.berill.core.sync.E2eState
import dev.akkraizen.berill.protocol.Collections
import dev.akkraizen.berill.protocol.SyncRecord
import dev.akkraizen.berill.sdk.protocol.LocalizedText
import dev.akkraizen.berill.sdk.protocol.ManifestPage
import dev.akkraizen.berill.sdk.protocol.PluginClassRules
import dev.akkraizen.berill.sdk.protocol.PluginManifest
import dev.akkraizen.berill.sdk.protocol.problems
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class SecurityTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @AfterTest
  fun tearDown() = scope.cancel()

  @Test
  fun permissionsAllowOnlyDeclaredHttpsHosts() {
    val p = PluginPermissions(network = listOf("api.example.com", "*.cdn.example.com"))
    assertTrue(p.allowsRequest("https://api.example.com/v1?q=1"))
    assertFalse(p.allowsRequest("http://api.example.com/v1"))
    assertFalse(p.allowsRequest("https://evil.com/?api.example.com"))
    assertFalse(p.allowsRequest("https://api.example.com@evil.com/"))
    assertTrue(p.allowsRequest("https://img.cdn.example.com/a.png"))
    assertFalse(p.allowsRequest("https://cdn.example.com/a.png"))
    assertFalse(PluginPermissions.None.allowsRequest("https://api.example.com"))
    assertFalse(p.allowsImage("file:///C:/secret.png"))
    assertTrue(PluginPermissions(anyImageHost = true).allowsImage("http://any.site/a.jpg"))
    assertTrue(hostMatches("a.b.com", "*.b.com"))
    assertFalse(hostMatches("ab.com", "*.b.com"))
  }

  @Test
  fun onlyWebAndMailLinksAreOpened() {
    assertTrue(isSafeExternalLink("https://github.com/x"))
    assertTrue(isSafeExternalLink("mailto:me@example.com"))
    assertFalse(isSafeExternalLink("file:///C:/Windows/System32/calc.exe"))
    assertFalse(isSafeExternalLink("javascript:alert(1)"))
    assertFalse(isSafeExternalLink("ms-settings:privacy"))
    assertFalse(isSafeExternalLink("https://bank.ru@evil.com/"))
  }

  @Test
  fun pluginClassRules() {
    assertTrue(PluginClassRules.isAllowed("java.util.ArrayList"))
    assertTrue(PluginClassRules.isAllowed("kotlin.collections.CollectionsKt"))
    assertTrue(PluginClassRules.isAllowed("dev.akkraizen.berill.sdk.UiBuilder"))
    assertFalse(PluginClassRules.isAllowed("java.io.File"))
    assertFalse(PluginClassRules.isAllowed("java.net.Socket"))
    assertFalse(PluginClassRules.isAllowed("java.lang.Runtime"))
    assertFalse(PluginClassRules.isAllowed("java.lang.Thread"))
    assertFalse(PluginClassRules.isAllowed("java.lang.reflect.Method"))
    assertFalse(PluginClassRules.isAllowed("kotlin.io.FilesKt"))
    assertFalse(PluginClassRules.isAllowed("dev.akkraizen.berill.core.BerillCore"))
    assertFalse(PluginClassRules.isAllowed("dev.akkraizen.berill.sdk.protocol.ToRunner"))
  }

  @Test
  fun manifestValidation() {
    val ok = PluginManifest(
      id = "counters",
      version = "1.0",
      name = LocalizedText("ru" to "Счётчики"),
      entry = "com.example.Counters",
      pages = listOf(ManifestPage("main", LocalizedText("ru" to "Главная"))),
      collections = listOf("counters_items")
    )
    assertEquals(emptyList(), ok.problems())
    assertTrue(ok.copy(collections = listOf("books")).problems().isNotEmpty())
    assertTrue(ok.copy(entry = "kotlin.Evil").problems().isNotEmpty())
    assertTrue(ok.copy(pages = emptyList()).problems().isNotEmpty())
  }

  @Test
  fun endToEncryptionHidesValuesAndRoundTrips() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val vault = MemoryVault()
    val core = BerillCore(driver, emptyList(), scope, vault = vault)
    core.store.put("notes", "n1", mapOf("title" to JsonPrimitive("Секрет")))
    assertEquals(E2eState.Off, core.encryption.state.value)

    core.encryption.enable("correct horse battery")
    assertEquals(E2eState.On, core.encryption.state.value)
    val note = core.store.get("notes", "n1")!!
    val sent = core.encryption.encryptOutgoing(note)!!
    val value = sent.fields["title"]!!.jsonPrimitive.content
    assertTrue(value.startsWith("e1:"))
    assertFalse("Секрет" in value)
    assertEquals("Секрет", core.encryption.decryptIncoming(sent)!!.fields["title"]!!.jsonPrimitive.content)

    val params = core.store.get(Collections.SETTINGS, "e2e")!!
    assertEquals(params, core.encryption.encryptOutgoing(params))

    val driver2 = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver2)
    val other = BerillCore(driver2, emptyList(), scope, vault = MemoryVault())
    other.store.applyRemote(listOf(params))
    other.encryption.refresh()
    assertEquals(E2eState.Locked, other.encryption.state.value)
    assertNull(other.encryption.decryptIncoming(sent))
    assertNull(other.encryption.encryptOutgoing(SyncRecord("notes", "n2")))
    assertFalse(other.encryption.unlock("wrong password"))
    assertTrue(other.encryption.unlock("correct horse battery"))
    assertNotNull(other.encryption.decryptIncoming(sent))
    assertNotEquals(sent, other.encryption.encryptOutgoing(note))
  }
}
