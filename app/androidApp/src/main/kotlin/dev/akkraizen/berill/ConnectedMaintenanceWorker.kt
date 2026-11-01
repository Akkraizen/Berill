package dev.akkraizen.berill

import android.content.Context
import androidx.work.*
import dev.akkraizen.berill.api.MaintenanceSource
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class ConnectedMaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    return try {
      val core = (applicationContext as BerillApplication).core
      for (plugin in core.plugins.enabled.value) {
        for (source in plugin.provides(core.plugins.context(plugin)).filterIsInstance<MaintenanceSource>()) source.update()
      }
      core.reminders.refresh(core.plugins)
      Result.success()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() }
  }
  companion object {
    fun register(context: Context) {
      val request = PeriodicWorkRequestBuilder<ConnectedMaintenanceWorker>(1, TimeUnit.HOURS).build()
      WorkManager.getInstance(context).enqueueUniquePeriodicWork("connected-maintenance", ExistingPeriodicWorkPolicy.KEEP, request)
    }
  }
}
