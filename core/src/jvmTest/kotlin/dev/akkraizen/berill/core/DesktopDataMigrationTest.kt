package dev.akkraizen.berill.core

import dev.akkraizen.berill.core.secure.DesktopVault
import dev.akkraizen.berill.core.secure.Dpapi
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopDataMigrationTest {
  @Test
  fun migratesConsistentDatabaseAndSecretsWithoutRemovingOriginalsOrOverwritingNewData() {
    val home = Files.createTempDirectory("berill-migration-").toFile()
    try {
      Class.forName("org.sqlite.JDBC")
      val name = previousInstallationName()
      val previous = File(home, ".$name").apply { mkdirs() }
      val oldDatabase = File(previous, "$name.db")
      DriverManager.getConnection("jdbc:sqlite:${oldDatabase.absolutePath}").use { old ->
        old.createStatement().use { sql ->
          sql.execute("PRAGMA journal_mode=WAL")
          sql.execute("CREATE TABLE migration_test (value TEXT)")
          sql.execute("INSERT INTO migration_test VALUES ('saved')")
        }
        val oldSecret = File(previous, "secrets/key").apply { parentFile.mkdirs(); writeText("saved-key") }
        val target = desktopDataDirectory(home)
        assertEquals(".berill", target.name)
        assertTrue(oldDatabase.isFile)
        assertTrue(oldSecret.isFile)
        assertEquals("saved-key", File(target, "secrets/key").readText())
        DriverManager.getConnection("jdbc:sqlite:${File(target, "berill.db").absolutePath}").use { migrated ->
          migrated.createStatement().use { sql ->
            sql.executeQuery("SELECT value FROM migration_test").use { result ->
              assertTrue(result.next()); assertEquals("saved", result.getString(1))
            }
            sql.executeUpdate("UPDATE migration_test SET value = 'updated'")
          }
        }
        File(target, "secrets/key").writeText("updated-key")
        desktopDataDirectory(home)
        assertEquals("updated-key", File(target, "secrets/key").readText())
        DriverManager.getConnection("jdbc:sqlite:${File(target, "berill.db").absolutePath}").use { migrated ->
          migrated.createStatement().use { sql ->
            sql.executeQuery("SELECT value FROM migration_test").use { result ->
              assertTrue(result.next()); assertEquals("updated", result.getString(1))
            }
          }
        }
      }
    } finally { home.deleteRecursively() }
  }

  @Test
  fun startsWithEmptyDataWhenNoPreviousInstallationExists() {
    val home = Files.createTempDirectory("berill-fresh-").toFile()
    try {
      val target = desktopDataDirectory(home)
      assertTrue(target.isDirectory)
      assertEquals(emptyList(), target.listFiles().orEmpty().toList())
    } finally { home.deleteRecursively() }
  }

  @Test
  fun windowsVaultReadsPreviousEncryptionAndWritesUsingCurrentEncryption() {
    if (!System.getProperty("os.name").startsWith("Windows")) return
    val dir = Files.createTempDirectory("berill-vault-").toFile()
    try {
      val plaintext = "existing-secret".encodeToByteArray()
      val encrypted = Dpapi.protect(plaintext, previousVaultEntropy())!!
      File(dir, "key").writeText(Base64.getEncoder().encodeToString(encrypted))
      val vault = DesktopVault(dir)
      assertEquals("existing-secret", vault.read("key"))
      vault.write("key", "new-secret")
      assertEquals("new-secret", vault.read("key"))
      assertContentEquals("new-secret".encodeToByteArray(),
        Dpapi.unprotect(Base64.getDecoder().decode(File(dir, "key").readText())))
    } finally { dir.deleteRecursively() }
  }
}
