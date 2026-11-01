package dev.akkraizen.berill.core.connections

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.akkraizen.berill.core.notifications.*
import dev.akkraizen.berill.api.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

object ReminderPermission {
  private var launch: (() -> Unit)? = null
  private var pending: ((Boolean) -> Unit)? = null
  private val mutex = Mutex()
  fun register(activity: ComponentActivity) {
    val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> pending?.invoke(allowed); pending = null }
    launch = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
  }
  suspend fun request(context: Context): Boolean = mutex.withLock {
    if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return@withLock true
    withContext(Dispatchers.Main) {
      val ask = launch ?: return@withContext false
      suspendCancellableCoroutine { cont ->
        pending = { if (cont.isActive) cont.resume(it) }
        cont.invokeOnCancellation { pending = null }
        ask()
      }
    }
  }
}

class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {
  private val prefs = context.getSharedPreferences("reminder-queue", Context.MODE_PRIVATE)
  private val alarms = context.getSystemService(AlarmManager::class.java)
  override suspend fun requestPermission() = ReminderPermission.request(context)
  override fun configure(preferences: NotificationPreferences, activePlugins: Set<String>) {
    prefs.edit().putString("policy", Json.encodeToString(NotificationPreferences.serializer(), preferences)).putStringSet("activePlugins", activePlugins).apply()
  }
  private fun intent(id: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
    Intent(context, ReminderReceiver::class.java).setAction("berill.REMIND").setData(Uri.parse("berill://reminder/" + Uri.encode(id))).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  @Synchronized override fun cancel(id: String) { alarms.cancel(intent(id)); prefs.edit().remove("item:$id").apply() }
  @Synchronized override fun replace(group: String, reminders: List<Reminder>) {
    val entries = reminders.filter { it.atMillis > System.currentTimeMillis() }.sortedBy { it.atMillis }.take(128)
    val previous = prefs.getStringSet("group:$group", emptySet()).orEmpty()
    val ids = entries.map { it.id }.toSet()
    (previous - ids).forEach(::cancel)
    for (reminder in entries) {
      val encoded = Json.encodeToString(Reminder.serializer(), reminder)
      if (prefs.getString("item:${reminder.id}", null) == encoded) continue
      prefs.edit().putString("item:${reminder.id}", encoded).apply()
      alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, intent(reminder.id))
    }
    prefs.edit().putStringSet("group:$group", ids).apply()
  }
  fun restore() {
    for ((key, value) in prefs.all) {
      if (!key.startsWith("item:") || value !is String) continue
      val reminder = runCatching { Json.decodeFromString(Reminder.serializer(), value) }.getOrNull() ?: continue
      if (reminder.atMillis > System.currentTimeMillis()) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, intent(reminder.id))
      else prefs.edit().remove(key).apply()
    }
  }
}

class ReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
      AndroidReminderScheduler(context).restore(); return
    }
    val id = intent.getStringExtra("id") ?: return
    val prefs = context.getSharedPreferences("reminder-queue", Context.MODE_PRIVATE)
    val reminder = prefs.getString("item:$id", null)?.let { runCatching { Json.decodeFromString(Reminder.serializer(), it) }.getOrNull() } ?: return
    prefs.edit().remove("item:$id").apply()
    val policy = prefs.getString("policy", null)?.let { runCatching { Json.decodeFromString(NotificationPreferences.serializer(), it) }.getOrNull() } ?: NotificationPreferences()
    if (!policy.allows(reminder) || reminder.owner()?.let { owner -> prefs.contains("activePlugins") && owner !in prefs.getStringSet("activePlugins", emptySet()).orEmpty() } == true) return
    val silent = policy.silent(reminder, System.currentTimeMillis())
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = if (silent) "berill-reminders-silent" else "berill-reminders"
    manager.createNotificationChannel(NotificationChannel(channel, "Berill", if (silent) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH).apply {
      if (silent) { setSound(null, null); enableVibration(false) }
    })
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      .putExtra("berill.reminder.action", Json.encodeToString(dev.akkraizen.berill.api.widgets.WidgetAction.serializer(), reminder.open))
    val open = PendingIntent.getActivity(context, id.hashCode(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = Notification.Builder(context, channel).setSmallIcon(android.R.drawable.ic_popup_reminder)
      .setOnlyAlertOnce(true).setContentTitle(reminder.title).setContentText(reminder.body).setAutoCancel(true).setContentIntent(open).build()
    if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
      manager.notify(id, 1, notification)
  }
}
