package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.HealthDataSink
import dev.akkraizen.berill.api.HealthDay
import dev.akkraizen.berill.api.HealthSummarySource
import dev.akkraizen.berill.api.all
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.plugins.health.HealthPlugin
import dev.akkraizen.berill.plugins.health.data.ActivityDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HealthStepsTest {
  private val core = run {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    BerillCore(driver, listOf(HealthPlugin), CoroutineScope(SupervisorJob() + Dispatchers.Default))
  }
  private val ctx = core.plugins.context(HealthPlugin)
  private val days = ctx.data.collection(HealthPlugin.ACTIVITY, ActivityDay.serializer())
  private val sink = HealthPlugin.provides(ctx).filterIsInstance<HealthDataSink>().single()
  private val date = today().toString()

  @Test
  fun manualStepsSurviveHealthConnectImportsButSleepStillUpdates() {
    days.save(date, ActivityDay(date, 4200, manualSteps = true))
    sink.accept(HealthDay(date, steps = 9000, sleepMinutes = 420))
    val day = days.get(date)!!
    assertEquals(4200L, day.steps)
    assertEquals(420L, day.sleepMinutes)
    val summary = core.plugins.capabilities.all<HealthSummarySource>().mapNotNull { it.day(today()) }.single()
    assertEquals(4200L, summary.steps)
  }

  @Test
  fun importedStepsAreUsedWhenNothingWasEnteredManually() {
    sink.accept(HealthDay(date, steps = 9000))
    sink.accept(HealthDay(date, steps = 9500))
    assertEquals(9500L, days.get(date)!!.steps)
  }
}
