package dev.akkraizen.berill.core

import dev.akkraizen.berill.core.log.AppLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal fun coreScope(): CoroutineScope =
  CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> AppLog.error("coroutine", "unhandled", e) }
  )

internal fun logStartup(platform: String) {
  val build = "${BuildInfo.VERSION_CODE}, ${BuildInfo.COMMIT}${if (BuildInfo.DIRTY) "+" else ""}"
  AppLog.info("app", "Berill ${BuildInfo.VERSION_NAME} ($build) $platform")
}
