package dev.akkraizen.berill.core

import androidx.compose.runtime.Composable
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.external.DesktopExternalPlugins
import dev.akkraizen.berill.core.files.DesktopFileAccess
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.log.installFileLog
import dev.akkraizen.berill.core.platform.DesktopIntegration
import dev.akkraizen.berill.core.secure.DesktopVault
import java.awt.Desktop
import java.io.File
import java.util.Properties

fun createDesktopCore(plugins: List<BerillPlugin>, desktop: DesktopIntegration? = null): BerillCore {
  val dir = desktopDataDirectory(File(System.getProperty("user.home")))
  val logs = File(dir, "logs")
  installFileLog(logs)
  AppLog.openLocation = { runCatching { Desktop.getDesktop().open(logs) } }
  logStartup(
    "${System.getProperty("os.name")} ${System.getProperty("os.version")}/JVM ${System.getProperty("java.version")}, " +
      "data: ${dir.absolutePath}"
  )
  val driver =
    JdbcSqliteDriver("jdbc:sqlite:${File(dir, "berill.db").absolutePath}", Properties(), BerillDatabase.Schema)
  return BerillCore(
    driver,
    plugins,
    coreScope(),
    DesktopFileAccess(),
    externalPlatform = runCatching { DesktopExternalPlugins(File(dir, "plugins")) }.getOrNull(),
    vault = DesktopVault(File(dir, "secrets")),
    desktop = desktop
  )
    .also { it.start() }
}

@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
