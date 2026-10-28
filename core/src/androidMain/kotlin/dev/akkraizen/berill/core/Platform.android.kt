package dev.akkraizen.berill.core

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.external.AndroidExternalPlugins
import dev.akkraizen.berill.core.files.AndroidFileAccess
import dev.akkraizen.berill.core.log.installFileLog
import dev.akkraizen.berill.core.secure.AndroidVault
import dev.akkraizen.berill.core.secure.LoopbackRedirects
import java.io.File

fun createAndroidCore(context: Context, plugins: List<BerillPlugin>): BerillCore {
  installFileLog(File(context.applicationContext.filesDir, "logs")) { line ->
    runCatching { Log.println(if (line.contains(" ERROR ")) Log.ERROR else Log.INFO, "Berill", line) }
  }
  logStartup("Android ${Build.VERSION.RELEASE} (${Build.MODEL})")
  return BerillCore(
    driver = AndroidSqliteDriver(BerillDatabase.Schema, context.applicationContext, "berill.db"),
    plugins = plugins,
    scope = coreScope(),
    files = AndroidFileAccess(context.applicationContext),
    externalPlatform = runCatching {
      AndroidExternalPlugins(context, File(context.applicationContext.filesDir, "plugins"))
    }.getOrNull(),
    vault = AndroidVault(context.applicationContext),
    oauth = LoopbackRedirects()
  )
    .also { it.start() }
}

@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
