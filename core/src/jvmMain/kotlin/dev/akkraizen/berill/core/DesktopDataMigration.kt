package dev.akkraizen.berill.core

import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.sql.DriverManager

internal fun desktopDataDirectory(home: File): File {
  val target = File(home, ".berill").apply {
    check(isDirectory || mkdirs()) { "Cannot create application data directory" }
  }
  val previousName = previousInstallationName()
  val previous = File(home, ".$previousName")
  val database = File(target, "berill.db")
  val previousDatabase = File(previous, "$previousName.db")
  if (!database.exists() && previousDatabase.isFile) {
    Class.forName("org.sqlite.JDBC")
    val temporary = Files.createTempFile(target.toPath(), ".migration-", ".db")
    try {
      DriverManager.getConnection("jdbc:sqlite:${previousDatabase.absolutePath}").use { connection ->
        connection.createStatement().use { statement ->
          val destination = temporary.toAbsolutePath().toString().replace("'", "''")
          statement.execute("VACUUM INTO '$destination'")
        }
      }
      try {
        Files.move(temporary, database.toPath())
      } catch (_: FileAlreadyExistsException) {
      }
    } finally {
      Files.deleteIfExists(temporary)
    }
  }
  val previousSecrets = File(previous, "secrets")
  if (previousSecrets.isDirectory) {
    val secrets = File(target, "secrets").apply {
      check(isDirectory || mkdirs()) { "Cannot create secret storage directory" }
    }
    previousSecrets.listFiles().orEmpty().filter { it.isFile }.forEach { source ->
      try {
        Files.copy(source.toPath(), File(secrets, source.name).toPath())
      } catch (_: FileAlreadyExistsException) {
      }
    }
  }
  return target
}
