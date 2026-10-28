package dev.akkraizen.berill.widgets

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetActionCallback : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val action = parameters[ACTION] ?: return
    WidgetHost.perform(context, glanceId, action)
  }

  companion object {
    val ACTION = ActionParameters.Key<String>("berill.widget.action")
  }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    WidgetHost.refreshAll(applicationContext)
    return Result.success()
  }
}
