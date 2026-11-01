package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.plugins.books.BooksPlugin
import dev.akkraizen.berill.plugins.finance.FinancePlugin
import dev.akkraizen.berill.plugins.habits.HabitsPlugin
import dev.akkraizen.berill.plugins.planner.PlannerPlugin
import dev.akkraizen.berill.plugins.planner.data.PlannerTask
import dev.akkraizen.berill.plugins.planner.data.CalendarEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import kotlin.test.*

class McpPluginToolsTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val core: BerillCore
  init { BerillDatabase.Schema.create(driver); core = BerillCore(driver, BerillPlugins, scope) }
  @AfterTest fun close() { scope.cancel(); driver.close() }
  private fun model(plugin: BerillPlugin, collection: String) = plugin.provides(core.plugins.context(plugin)).filterIsInstance<McpSource>().single().collections.single { it.name == collection }
  @Test fun pluginModelsValidateCreatePatchAndGuardAgainstStaleEdits() {
    val books = model(BooksPlugin, BooksPlugin.BOOKS)
    val created = books.create(buildJsonObject { put("title", "Agent book") })
    val id = created.getValue("id").jsonPrimitive.content
    val expected = created.getValue("expected").jsonObject
    val changed = books.update(id, buildJsonObject { put("title", "Updated") }, expected)
    assertEquals("Updated", changed.getValue("fields").jsonObject.getValue("title").jsonPrimitive.content)
    assertFailsWith<McpException> { books.update(id, buildJsonObject { put("title", "Stale") }, expected) }
    assertFailsWith<IllegalArgumentException> { books.create(buildJsonObject { put("title", ""); put("unknown", true) }) }
    assertFailsWith<IllegalArgumentException> { model(FinancePlugin, FinancePlugin.OPERATIONS).create(buildJsonObject { put("amount", -5); put("date", "2026-10-30") }) }
    assertTrue(books.schema.getValue("properties").jsonObject.containsKey("title"))
    assertFailsWith<IllegalArgumentException> { books.update(id, buildJsonObject { put("status", "TYPO") }, changed.getValue("expected").jsonObject) }
    val finished = books.update(id, buildJsonObject { put("status", "DONE") }, changed.getValue("expected").jsonObject)
    assertTrue(finished.getValue("fields").jsonObject.getValue("finished") != JsonNull)
  }
  @Test fun listsCarryShortEtagsThatGuardEditsLikeExpectedFields() {
    val books = model(BooksPlugin, BooksPlugin.BOOKS)
    val id = books.create(buildJsonObject { put("title", "Etag book") }).getValue("id").jsonPrimitive.content
    val listed = books.list(50, 0).getValue("records").jsonArray.single().jsonObject
    assertFalse("expected" in listed, "lists do not repeat the fields")
    val etag = listed.getValue("etag").jsonPrimitive.content
    assertEquals(16, etag.length)
    assertEquals(etag, books.get(id).getValue("etag").jsonPrimitive.content)
    books.update(id, buildJsonObject { put("title", "Edited") }, books.expectedFor(id, etag, null))
    val stale = assertFailsWith<McpException> { books.expectedFor(id, etag, null) }
    assertEquals(McpErrorCode.CONFLICT, stale.code)
    assertNotEquals(etag, books.get(id).getValue("etag").jsonPrimitive.content)
  }
  @Test fun habitProgressUsesDateIdsAndPreservesOtherHabits() {
    val definitions = model(HabitsPlugin, HabitsPlugin.HABITS)
    val habit = definitions.create(buildJsonObject { put("label", "Read"); put("defaultDuration", 1) }).getValue("id").jsonPrimitive.content
    val days = model(HabitsPlugin, HabitsPlugin.HABIT_DAYS)
    val day = days.create(buildJsonObject { put("date", "2026-10-30"); put("values", buildJsonObject { put(habit, 1) }) })
    days.update("2026-10-30", buildJsonObject { put("values", buildJsonObject { put(habit, 2) }) }, day.getValue("expected").jsonObject)
    assertEquals(2, core.data.raw(HabitsPlugin.HABIT_DAYS).get("2026-10-30")!!.fields.getValue(habit).jsonPrimitive.int)
  }
  @Test fun plannerMcpReschedulingKeepsTransferHistoryAndGoogleDeleteKeepsSyncWork() {
    val tasks = core.data.collection<PlannerTask>(PlannerPlugin.TASKS)
    tasks.save("task", PlannerTask("Move", "2026-10-29", done = true))
    val api = model(PlannerPlugin, PlannerPlugin.TASKS)
    api.update("task", buildJsonObject { put("date", "2026-10-30") }, api.get("task").getValue("expected").jsonObject)
    assertEquals(listOf("2026-10-29"), tasks.get("task")!!.deferredDates)
    assertFalse(tasks.get("task")!!.done)
    core.data.collection<CalendarEvent>(PlannerPlugin.EVENTS).save("event", CalendarEvent("Meeting", "2026-10-30", googleId = "google", calendarId = "calendar"))
    val events = model(PlannerPlugin, PlannerPlugin.EVENTS)
    events.delete("event", events.get("event").getValue("expected").jsonObject, true)
    val event = core.data.collection<CalendarEvent>(PlannerPlugin.EVENTS).get("event")!!
    assertTrue(event.deleted); assertTrue(event.dirty)
  }
  @Test fun disabledPluginsNoLongerExposeMcpCapabilitiesAndKeepTheirData() = runBlocking {
    val books = model(BooksPlugin, BooksPlugin.BOOKS)
    books.create(buildJsonObject { put("title", "Keep me") })
    core.plugins.setEnabled("books", false)
    withTimeout(5000) { core.plugins.enabled.first { list -> list.none { it.id == "books" } } }
    assertTrue(core.plugins.capabilities.all<McpSource>().none { it.pluginId == "books" })
    assertEquals(1, core.data.raw(BooksPlugin.BOOKS).all().size)
    core.plugins.setEnabled("books", true)
    withTimeout(5000) { core.plugins.enabled.first { list -> list.any { it.id == "books" } } }
    assertTrue(core.plugins.capabilities.all<McpSource>().any { it.pluginId == "books" })
  }

  @Test fun queuedTimersRespectNewFiltersAndPluginDisableWithoutResurrectionAfterCancel() = runBlocking {
    class Scheduler : dev.akkraizen.berill.core.connections.ReminderScheduler {
      val groups = mutableMapOf<String, List<Reminder>>()
      override suspend fun requestPermission() = true
      override fun replace(group: String, reminders: List<Reminder>) { groups[group] = reminders }
      override fun cancel(id: String) { groups.keys.toList().forEach { key -> groups[key] = groups[key].orEmpty().filterNot { it.id == id } } }
    }
    val scheduler = Scheduler()
    val reminders = dev.akkraizen.berill.core.connections.ReminderCenter(core.store, scheduler, core.notificationPreferences) { throw it }
    reminders.refresh(core.plugins)
    reminders.schedule(Reminder("rest", "Rest", "Done", System.currentTimeMillis() + 600_000, pluginId = "workouts", event = ReminderEvent.TIMER))
    assertEquals(1, scheduler.groups.getValue("timer:rest").size)
    core.notificationPreferences.set(dev.akkraizen.berill.core.notifications.NotificationPreferences(plugins = mapOf("workouts" to dev.akkraizen.berill.core.notifications.PluginNotificationPreferences(hiddenEvents = setOf(ReminderEvent.TIMER)))))
    reminders.refresh(core.plugins); assertTrue(scheduler.groups.getValue("timer:rest").isEmpty())
    core.notificationPreferences.reset(); reminders.refresh(core.plugins)
    assertEquals(1, scheduler.groups.getValue("timer:rest").size)
    core.plugins.setEnabled("workouts", false)
    withTimeout(5000) { core.plugins.enabled.first { list -> list.none { it.id == "workouts" } } }
    reminders.refresh(core.plugins); assertTrue(scheduler.groups.getValue("timer:rest").isEmpty())
    core.plugins.setEnabled("workouts", true)
    withTimeout(5000) { core.plugins.enabled.first { list -> list.any { it.id == "workouts" } } }
    reminders.refresh(core.plugins); assertEquals(1, scheduler.groups.getValue("timer:rest").size)
    reminders.cancel("rest"); reminders.refresh(core.plugins)
    assertTrue(scheduler.groups.getValue("timer:rest").isEmpty())
  }
}
