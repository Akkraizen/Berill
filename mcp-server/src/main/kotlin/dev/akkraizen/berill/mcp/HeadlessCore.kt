package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.BerillPlugins
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.log.installFileLog
import dev.akkraizen.berill.core.openSqliteDriver
import dev.akkraizen.berill.core.secure.DesktopVault
import dev.akkraizen.berill.core.secure.SecretVault
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun defaultDataDirectory(): File = File(System.getProperty("user.home"), ".berill-mcp")

fun openCore(dir: File, vault: SecretVault = DesktopVault(File(dir, "secrets"))): BerillCore {
  check(dir.isDirectory || dir.mkdirs()) { "Cannot create ${dir.absolutePath}" }
  installFileLog(File(dir, "logs"))
  AppLog.info("mcp", "Berill MCP server, data: ${dir.absolutePath}")
  val driver = openSqliteDriver(File(dir, "berill.db"))
  val scope = CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> AppLog.error("mcp", "unhandled", e) }
  )
  return BerillCore(driver, BerillPlugins, scope, vault = vault)
}
