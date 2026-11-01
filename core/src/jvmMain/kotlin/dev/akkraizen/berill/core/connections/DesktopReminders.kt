package dev.akkraizen.berill.core.connections

import dev.akkraizen.berill.core.notifications.*
import dev.akkraizen.berill.api.Reminder
import java.awt.SystemTray
import java.awt.TrayIcon
import java.util.Timer
import java.util.TimerTask

class DesktopReminderScheduler : ReminderScheduler {
  @Volatile private var preferences = NotificationPreferences()
  @Volatile private var activePlugins: Set<String>? = null
  override fun configure(preferences: NotificationPreferences, activePlugins: Set<String>) { this.preferences = preferences; this.activePlugins = activePlugins }
  private val timer = Timer("berill-reminders", true)
  private val tasks = mutableMapOf<String, Pair<Reminder, TimerTask>>()
  private val groups = mutableMapOf<String, Set<String>>()
  override suspend fun requestPermission() = SystemTray.isSupported()
  @Synchronized override fun cancel(id: String) { tasks.remove(id)?.second?.cancel() }
  @Synchronized override fun replace(group: String, reminders: List<Reminder>) {
    val ids = reminders.map { it.id }.toSet()
    (groups[group].orEmpty() - ids).forEach(::cancel)
    for (reminder in reminders) {
      if (tasks[reminder.id]?.first == reminder) continue
      cancel(reminder.id)
      val task = object : TimerTask() { override fun run() {
        if (SystemTray.isSupported()) java.awt.EventQueue.invokeLater {
          val policy = preferences
          if (policy.allows(reminder) && reminder.owner()?.let { activePlugins?.contains(it) } != false) {
            val silent = policy.silent(reminder, System.currentTimeMillis())
            if (!silent) SystemTray.getSystemTray().trayIcons.firstOrNull()?.displayMessage(reminder.title, reminder.body, TrayIcon.MessageType.INFO)
            else SilentDesktopNotification.show(reminder.title, reminder.body)
          }
        }
      } }
      tasks[reminder.id] = reminder to task
      timer.schedule(task, (reminder.atMillis - System.currentTimeMillis()).coerceAtLeast(1))
    }
    groups[group] = ids
  }
}
