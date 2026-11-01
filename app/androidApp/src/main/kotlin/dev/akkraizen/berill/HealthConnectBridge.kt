package dev.akkraizen.berill

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.*
import androidx.health.connect.client.time.TimeRangeFilter
import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.connections.HealthImport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume

object HealthPermissions {
  var launch: ((Set<String>) -> Unit)? = null
  var pending: ((Set<String>) -> Unit)? = null
  fun register(activity: ComponentActivity) {
    val launcher = activity.registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted -> pending?.invoke(granted); pending = null }
    launch = { launcher.launch(it) }
  }
}

class HealthConnectBridge(private val context: Context, private val core: BerillCore) : HealthImport {
  override val status = MutableStateFlow("")
  private val mutex = Mutex()
  private val permissions = setOf(HealthPermission.getReadPermission(WeightRecord::class), HealthPermission.getReadPermission(StepsRecord::class), HealthPermission.getReadPermission(SleepSessionRecord::class))
  override suspend fun connect() {
    if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
      status.value = dev.akkraizen.berill.api.i18n.I18n.text("errors.healthUnavailable")
      withContext(Dispatchers.Main) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
      return
    }
    val client = HealthConnectClient.getOrCreate(context)
    if (!client.permissionController.getGrantedPermissions().containsAll(permissions)) {
      val granted = withContext(Dispatchers.Main) { suspendCancellableCoroutine<Set<String>> { cont ->
        val ask = HealthPermissions.launch
        if (ask == null) { cont.resume(emptySet()); return@suspendCancellableCoroutine }
        HealthPermissions.pending = { if (cont.isActive) cont.resume(it) }
        cont.invokeOnCancellation { HealthPermissions.pending = null }
        ask(permissions)
      } }
      if (!granted.containsAll(permissions)) { status.value = dev.akkraizen.berill.api.i18n.I18n.text("errors.permissionDenied"); return }
    }
    core.store.setMeta("health.enabled", "1")
    refresh()
  }
  override suspend fun refresh() = mutex.withLock {
    try {
      if (core.store.meta("health.enabled") != "1" || HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return@withLock
      val client = HealthConnectClient.getOrCreate(context)
      if (!client.permissionController.getGrantedPermissions().containsAll(permissions)) { status.value = dev.akkraizen.berill.api.i18n.I18n.text("errors.permissionDenied"); return@withLock }
      val sinks = core.plugins.enabled.value.flatMap { it.provides(core.plugins.context(it)).filterIsInstance<HealthDataSink>() }
      val zone = ZoneId.systemDefault()
      for (offset in 0L..6L) {
        val day = LocalDate.now().minusDays(offset)
        val range = TimeRangeFilter.between(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant())
        val aggregate = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL, SleepSessionRecord.SLEEP_DURATION_TOTAL), range))
        val weights = mutableListOf<WeightRecord>()
        var page: String? = null
        do {
          val result = client.readRecords(ReadRecordsRequest(WeightRecord::class, range, pageToken = page))
          weights += result.records; page = result.pageToken
        } while (page != null)
        val health = HealthDay(day.toString(), aggregate[StepsRecord.COUNT_TOTAL], aggregate[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes(), weights.maxByOrNull { it.time }?.weight?.inKilograms)
        sinks.forEach { it.accept(health) }
      }
      status.value = dev.akkraizen.berill.api.i18n.I18n.text("errors.healthDone")
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
      dev.akkraizen.berill.core.log.AppLog.error("health-connect", "import", e)
      status.value = dev.akkraizen.berill.core.connections.failureMessage(e)
    }
  }
}
