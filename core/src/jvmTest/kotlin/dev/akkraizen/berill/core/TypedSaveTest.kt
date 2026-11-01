package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.data.StoreDataAccess
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.protocol.HybridClock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

@Serializable(with = StateSerializer::class)
enum class State { OPEN, DONE }
object StateSerializer : LegacyEnumSerializer<State>("test.state", State.entries, { if (it == State.OPEN) "open" else "done" }, State.OPEN)

@Serializable
data class Task(val title: String, val state: State = State.OPEN, val count: Int = 0)

class TypedSaveTest {
  @Test fun savingOneFieldKeepsValuesThisVersionDoesNotUnderstand() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    try {
      BerillDatabase.Schema.create(driver)
      val store = LocalStore(BerillDatabase(driver), HybridClock("typed") { 1L })
      val tasks = StoreDataAccess(store).collection("tasks", Task.serializer())
      // A newer client stored a state this version does not know, and a lenient number.
      store.put("tasks", "t1", mapOf("title" to JsonPrimitive("Old"), "state" to JsonPrimitive("archived"), "count" to JsonPrimitive("7")))
      val task = tasks.get("t1")!!
      assertEquals(State.OPEN, task.state)
      assertEquals(7, task.count)

      tasks.save("t1", task.copy(title = "New"))
      val fields = store.get("tasks", "t1")!!.fields
      assertEquals(JsonPrimitive("New"), fields["title"])
      assertEquals(JsonPrimitive("archived"), fields["state"], "an unchanged unknown value must survive")
      assertEquals(JsonPrimitive("7"), fields["count"])

      tasks.save("t1", tasks.get("t1")!!.copy(state = State.DONE))
      assertEquals(JsonPrimitive("done"), store.get("tasks", "t1")!!.fields["state"], "an explicit change is written")

      tasks.save("t2", Task("Fresh"))
      assertEquals(setOf("title", "state", "count"), store.get("tasks", "t2")!!.fields.keys)
    } finally { driver.close() }
  }

  @Test fun localChangesMadeWithAClockFarAheadAreReStampedBeforeUpload() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    try {
      BerillDatabase.Schema.create(driver)
      var now = 4_102_444_800_000L // the device clock is in 2100
      val store = LocalStore(BerillDatabase(driver), HybridClock("skewed") { now })
      store.put("notes", "n1", mapOf("body" to JsonPrimitive("written in the future")))
      now = 1_800_000_000_000L // the clock is fixed
      assertEquals(1, store.restampFutureChanges())
      val stamp = store.get("notes", "n1")!!.clocks.getValue("body")
      assertTrue(HybridClock.decode(stamp)!!.first <= now + HybridClock.MAX_DRIFT_MILLIS)
      assertEquals(0, store.restampFutureChanges())
      assertEquals(1, store.dirty().size)
    } finally { driver.close() }
  }
}
