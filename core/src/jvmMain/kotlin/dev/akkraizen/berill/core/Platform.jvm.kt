package dev.akkraizen.berill.core

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.BerillPlugin
import app.cash.sqldelight.db.SqlDriver
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.db.SharedSqliteDriver
import dev.akkraizen.berill.core.external.DesktopExternalPlugins
import dev.akkraizen.berill.core.files.DesktopFileAccess
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.log.installFileLog
import dev.akkraizen.berill.core.platform.DesktopIntegration
import dev.akkraizen.berill.core.platform.McpAccess
import dev.akkraizen.berill.core.secure.DesktopVault
import dev.akkraizen.berill.core.secure.LoopbackRedirects
import dev.akkraizen.berill.core.ui.theme.desktopAppearanceCache
import java.awt.Desktop
import java.io.File

fun createDesktopCore(
  plugins: List<BerillPlugin>,
  desktop: DesktopIntegration? = null,
  mcp: ((BerillCore) -> McpAccess)? = null
): BerillCore {
  val dir = desktopDataDirectory(File(System.getProperty("user.home")))
  val logs = File(dir, "logs")
  installFileLog(logs)
  AppLog.openLocation = { runCatching { Desktop.getDesktop().open(logs) } }
  logStartup(
    "${System.getProperty("os.name")} ${System.getProperty("os.version")}/JVM ${System.getProperty("java.version")}, " +
      "data: ${dir.absolutePath}"
  )
  val driver = openSqliteDriver(File(dir, "berill.db"))
  return BerillCore(
    driver,
    plugins,
    coreScope(),
    DesktopFileAccess(),
    updateInstaller = dev.akkraizen.berill.core.update.DesktopUpdateInstaller(File(dir, "updates")),
    externalPlatform = runCatching { DesktopExternalPlugins(File(dir, "plugins")) }.getOrNull(),
    attachmentCache = dev.akkraizen.berill.core.attachments.FileAttachmentCache(File(dir, "attachments")),
    reminderScheduler = dev.akkraizen.berill.core.connections.DesktopReminderScheduler(),
    vault = DesktopVault(File(dir, "secrets")),
    desktop = desktop,
    oauth = LoopbackRedirects(),
    appearanceCache = desktopAppearanceCache(),
    mcpFactory = mcp
  )
    .also { it.start() }
}

@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

fun openSqliteDriver(file: File): SqlDriver = SharedSqliteDriver.open(file, BerillDatabase.Schema)
