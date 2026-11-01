package dev.akkraizen.berill.core.connections

import dev.akkraizen.berill.api.FileAccess
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.preferences.LocalPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlin.time.Clock
import kotlin.time.Instant

/** Per-device settings: the folder reference only makes sense on the device that chose it. */
@Serializable
data class AutoExportSettings(val enabled: Boolean = false, val folder: String? = null, val intervalDays: Int = 1, val keep: Int = 7)

/** Result of the last automatic export, shown in Settings → Data. */
@Serializable
data class AutoExportStatus(val at: Long, val file: String? = null, val failure: FailureReason? = null)

/**
 * Saves a local archive into a chosen folder on a schedule and keeps the newest [AutoExportSettings.keep]
 * of them. Runs while the app is open (checked hourly) and from Android's hourly background work.
 */
class AutoExport(
  private val store: LocalStore,
  private val archive: LocalArchiveService,
  private val files: FileAccess,
  private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
  val preferences = LocalPreferences(store, "auto_export.settings", AutoExportSettings.serializer(), AutoExportSettings())
  private val statusPreferences = LocalPreferences(store, "auto_export.status", AutoExportStatus.serializer().nullable, null)
  val status = statusPreferences.state
  private val running = MutableStateFlow(false)
  val busy = running.asStateFlow()
  private val mutex = Mutex()

  fun isDue(): Boolean {
    val settings = preferences.state.value
    if (!settings.enabled || settings.folder == null) return false
    val last = status.value?.takeIf { it.failure == null }?.at ?: 0L
    return now() - last >= settings.intervalDays.coerceAtLeast(1) * DAY - GRACE
  }

  suspend fun runIfDue(): Boolean = if (isDue()) runNow() else false

  /** Exports immediately; returns true on success. Failures are recorded in [status], not thrown. */
  suspend fun runNow(): Boolean = mutex.withLock {
    val settings = preferences.state.value
    val folder = settings.folder ?: return@withLock false
    running.value = true
    try {
      val name = fileName(now())
      val bytes = archive.export()
      if (!files.writeToFolder(folder, name, bytes)) throw FeatureException(FailureReason.ACCESS_DENIED)
      prune(folder, settings.keep.coerceAtLeast(1))
      statusPreferences.set(AutoExportStatus(now(), name))
      AppLog.info(TAG, "saved $name (${bytes.size} bytes)")
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      AppLog.error(TAG, "automatic export failed", e)
      statusPreferences.set(AutoExportStatus(now(), failure = failureReason(e)))
      false
    } finally {
      running.value = false
    }
  }

  private suspend fun prune(folder: String, keep: Int) {
    val old = files.listFolder(folder).filter { NAME.matches(it) }.sortedDescending().drop(keep)
    for (name in old) runCatching { files.deleteFromFolder(folder, name) }.onFailure { AppLog.warn(TAG, "could not remove $name", it) }
  }

  companion object {
    private const val TAG = "auto-export"
    private const val DAY = 24L * 60 * 60 * 1000
    /** Starts a daily export a little early so a fixed daily routine does not drift by an hour. */
    private const val GRACE = 90L * 60 * 1000
    private val NAME = Regex("berill-auto-\\d{4}-\\d{2}-\\d{2}-\\d{4}\\.zip")

    fun fileName(at: Long): String {
      val time = Instant.fromEpochMilliseconds(at).toLocalDateTime(TimeZone.currentSystemDefault())
      fun two(value: Int) = value.toString().padStart(2, '0')
      return "berill-auto-${time.date}-${two(time.hour)}${two(time.minute)}.zip"
    }
  }
}
