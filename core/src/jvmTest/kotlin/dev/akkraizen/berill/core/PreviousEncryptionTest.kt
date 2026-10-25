package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.secure.MemoryVault
import dev.akkraizen.berill.core.secure.platformCrypto
import dev.akkraizen.berill.core.sync.E2eState
import dev.akkraizen.berill.protocol.Collections
import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviousEncryptionTest {
  @Test
  fun renamedAppUnlocksPreviousEncryptionProfileAndReadsExistingRecords() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val core = BerillCore(driver, emptyList(), scope, vault = MemoryVault())
      val crypto = platformCrypto()
      val previous = previousInstallationName()
      val salt = crypto.randomBytes(16)
      val password = "existing account password"
      val key = crypto.pbkdf2(password, salt, 1000, 32)
      val check = crypto.encrypt(key, "$previous-e2e-check".encodeToByteArray(),
        "$previous/e2e/check".encodeToByteArray())
      core.store.put(Collections.SETTINGS, "e2e", mapOf(
        "salt" to JsonPrimitive(Base64.getEncoder().encodeToString(salt)),
        "iterations" to JsonPrimitive(1000),
        "check" to JsonPrimitive(Base64.getEncoder().encodeToString(check))
      ))
      core.encryption.refresh()
      assertEquals(E2eState.Locked, core.encryption.state.value)
      assertFalse(core.encryption.unlock("wrong password"))
      assertTrue(core.encryption.unlock(password))
      val encrypted = crypto.encrypt(key, JsonPrimitive("saved note").toString().encodeToByteArray(),
        "notes/n1/title".encodeToByteArray())
      val record = SyncRecord("notes", "n1", fields = JsonObject(mapOf(
        "title" to JsonPrimitive("e1:" + Base64.getEncoder().encodeToString(encrypted)))))
      assertEquals("saved note", core.encryption.decryptIncoming(record)!!.fields["title"]!!.jsonPrimitive.content)
      assertEquals(E2eState.On, core.encryption.state.value)
    } finally { scope.cancel(); driver.close() }
  }
}
