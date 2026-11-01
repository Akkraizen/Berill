package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.ui.interaction.*
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.core.connections.*
import dev.akkraizen.berill.core.notifications.*
import dev.akkraizen.berill.core.preferences.*
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.protocol.HybridClock
import kotlinx.coroutines.*
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.test.*

class NotificationPreferencesTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  init { BerillDatabase.Schema.create(driver) }
  private val store = LocalStore(BerillDatabase(driver), HybridClock("test") { 1L })
  private val prefs = LocalPreferences(store, "notifications.preferences", NotificationPreferences.serializer(), NotificationPreferences())
  @AfterTest fun close() { driver.close() }
  private fun at(time: String) = Instant.parse("2026-10-30T${time}:00Z").toEpochMilliseconds()
  @Test fun quietHoursCoverMidnightAndExactBoundaries() {
    val p = NotificationPreferences(quietHours = true, quietStart = 22 * 60, quietEnd = 8 * 60)
    val utc = TimeZone.UTC
    assertFalse(p.quietAt(at("21:59"), utc)); assertTrue(p.quietAt(at("22:00"), utc))
    assertTrue(p.quietAt(at("00:00"), utc)); assertTrue(p.quietAt(at("07:59"), utc)); assertFalse(p.quietAt(at("08:00"), utc))
    assertTrue(p.copy(quietEnd = p.quietStart).quietAt(at("12:00"), utc))
    assertFalse(p.copy(quietHours = false).quietAt(at("23:00"), utc))
    assertTrue(p.copy(quietStart = 10 * 60, quietEnd = 12 * 60).quietAt(at("11:00"), utc))
  }
  @Test fun pluginFiltersAndSoundsAreIndependentAndPersist() {
    val reminder = Reminder("timer", "Rest", "Done", at("23:00"), pluginId = "workouts", event = ReminderEvent.TIMER)
    val rule = PluginNotificationPreferences(sound = false, hiddenEvents = setOf(ReminderEvent.DEADLINE))
    val p = NotificationPreferences(plugins = mapOf("workouts" to rule))
    assertTrue(p.allows(reminder)); assertTrue(p.silent(reminder, at("12:00")))
    assertFalse(p.allows(reminder.copy(event = ReminderEvent.DEADLINE)))
    assertFalse(p.copy(enabled = false).allows(reminder))
    assertFalse(p.copy(hiddenKinds = setOf(NoticeKind.REMINDER)).allows(reminder))
    prefs.set(p)
    assertEquals(p, LocalPreferences(store, "notifications.preferences", NotificationPreferences.serializer(), NotificationPreferences()).state.value)
    prefs.reset(); assertTrue(prefs.state.value.sound)
  }
  @Test fun filteringKeepsHistoryAndReadStateForReenabledEvents() {
    val center = NotificationCenter(store) { prefs.state.value }
    center.publish(Notice("update", NoticeKind.UPDATE)); center.markRead("update")
    prefs.set(NotificationPreferences(hiddenKinds = setOf(NoticeKind.UPDATE)))
    assertFalse(center.isVisible(center.state.value.single()))
    prefs.reset(); assertTrue(center.isVisible(center.state.value.single())); assertTrue(center.state.value.single().read)
    center.reminders("timer", listOf(Reminder("rest", "Rest", "Done", 10, WidgetAction.Open(pluginId = "workouts"), event = ReminderEvent.TIMER)))
    assertFalse(center.isVisible(center.state.value.first { it.kind == NoticeKind.REMINDER }, emptySet()))
    prefs.set(NotificationPreferences(plugins = mapOf("workouts" to PluginNotificationPreferences(hiddenEvents = setOf(ReminderEvent.TIMER)))))
    assertFalse(center.isVisible(center.state.value.first { it.kind == NoticeKind.REMINDER }))
  }
  @Test fun timerPoliciesCanBeChangedAndCancelledWithoutDiscardingOtherTimers() = runBlocking {
    class Scheduler : ReminderScheduler {
      val groups = mutableMapOf<String, List<Reminder>>()
      override suspend fun requestPermission() = true
      override fun replace(group: String, reminders: List<Reminder>) { groups[group] = reminders }
      override fun cancel(id: String) { groups.keys.toList().forEach { key -> groups[key] = groups[key].orEmpty().filterNot { it.id == id } } }
    }
    val scheduler = Scheduler()
    val center = ReminderCenter(store, scheduler, prefs) { throw it }
    val future = System.currentTimeMillis() + 60_000
    center.schedule(Reminder("a", "A", "A", future, pluginId = "workouts", event = ReminderEvent.TIMER))
    prefs.set(NotificationPreferences(enabled = false))
    center.schedule(Reminder("b", "B", "B", future, pluginId = "workouts", event = ReminderEvent.TIMER))
    assertTrue(scheduler.groups.getValue("timer:b").isEmpty())
    center.cancel("a")
    val restarted = ReminderCenter(store, scheduler, prefs) { throw it }
    restarted.cancel("b")
    assertTrue(store.meta("reminders.timers")!!.contains("{}"))
  }
  @Test fun escapeExitsFocusBeforeNavigatingAndDialogClosesFirst() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val core = BerillCore(driver, emptyList(), scope)
      core.finishOnboarding(); core.notes.save("n", "Title", "Body"); core.nav.openNote("n"); core.focusedNote.value = "n"
      val before = core.nav.stack.value
      core.notifications.open.value = true
      assertTrue(core.onEscapeKey()); assertEquals("n", core.focusedNote.value)
      assertTrue(core.onEscapeKey()); assertNull(core.focusedNote.value); assertEquals(before, core.nav.stack.value)
      assertEquals("Body", core.notes.get("n")!!.body)
      assertTrue(ShortcutAction.FOCUS.defaultBinding!!.matches(ShortcutKey.F, true, false, true, false))
    } finally { scope.cancel() }
  }
  @Test fun windowsBalloonAbiMatchesNativeLayoutWithoutShowingNotification() {
    if (System.getProperty("os.name").startsWith("Windows")) {
      assertEquals(if (com.sun.jna.Native.POINTER_SIZE == 8) 976 else 956, BalloonData().size())
    }
  }

  @Test fun legacySyncNoticesAreRemovedAndCannotReappear() {
    store.setMeta("notifications.v1", """[{"id":"sync","kind":"SYNC","status":"RUNNING"},{"id":"update","kind":"UPDATE"}]""")
    val center = NotificationCenter(store)
    assertEquals(listOf("update"), center.state.value.map { it.id })
    assertFalse(store.meta("notifications.v1")!!.contains("SYNC"))
    center.sync(dev.akkraizen.berill.core.sync.SyncState.Syncing)
    center.publish(Notice("sync", NoticeKind.SYNC, NoticeStatus.SUCCESS))
    assertEquals(listOf("update"), center.state.value.map { it.id })
    assertFalse(center.isVisible(Notice("sync", NoticeKind.SYNC)))
  }
}
