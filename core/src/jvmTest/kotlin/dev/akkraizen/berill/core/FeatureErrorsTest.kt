package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.i18n.*
import dev.akkraizen.berill.api.Reminder
import dev.akkraizen.berill.core.connections.*
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.protocol.HybridClock
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.*
import kotlin.test.*

class FeatureErrorsTest {
 @Test fun classifiesFailuresWithoutExposingExceptionMessages() {
  assertEquals(FailureReason.NETWORK, failureReason(RuntimeException(UnknownHostException("secret host"))))
  assertEquals(FailureReason.ACCESS_DENIED, failureReason(SecurityException("private path")))
  assertEquals(FailureReason.IO, failureReason(IOException("private data")))
  assertEquals(FailureReason.INVALID_DATA, failureReason(IllegalArgumentException("bad input")))
  val handler = FeatureErrors()
  assertFalse(handler.run(FailureOperation.EXPORT) { error("Bearer secret-token") })
  assertEquals(FailureOperation.EXPORT, handler.problem.value!!.operation)
  assertFalse(handler.problem.value!!.message().contains("secret-token"))
  handler.dismiss()
  assertTrue(handler.run(FailureOperation.EXPORT) { })
  assertNull(handler.problem.value)
 }
 @Test fun statusesHaveActionableTypedReasons() {
  for ((status, expected) in mapOf(401 to FailureReason.AUTH_REQUIRED, 403 to FailureReason.ACCESS_DENIED,
   404 to FailureReason.SERVER_NOT_FOUND, 413 to FailureReason.TOO_LARGE, 429 to FailureReason.SERVICE_UNAVAILABLE, 503 to FailureReason.SERVICE_UNAVAILABLE)) {
    val error = assertFailsWith<FeatureException> { checkServiceStatus(status) }
    assertEquals(expected, error.reason)
  }
  checkServiceStatus(200)
  checkServiceStatus(204)
 }
 @Test fun cancellationNeverBecomesAnErrorOrPartialResult() = runBlocking {
  val handler = FeatureErrors()
  assertFailsWith<CancellationException> { handler.run(FailureOperation.SEARCH) { throw CancellationException("cancelled") } }
  assertFailsWith<CancellationException> { handler.runSuspending(FailureOperation.IMPORT) { throw CancellationException("cancelled") } }
  var failed = false
  assertFailsWith<CancellationException> { tolerateSource("test", { failed = true }) { throw CancellationException("cancelled") } }
  assertFalse(failed)
  assertNull(handler.problem.value)
  Unit
 }
 @Test fun failingSourceDoesNotHideHealthySources() = runBlocking {
  var failures = 0
  val results = listOf("broken", "healthy").flatMap { source ->
    tolerateSource(source, { failures++ }) {
      if (source == "broken") error("broken plugin") else listOf("available record")
    }.orEmpty()
  }
  assertEquals(listOf("available record"), results)
  assertEquals(1, failures)
 }
 @Test fun permissionDenialDoesNotEnableRemindersAndCanBeRetried() = runBlocking {
  val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  try {
   BerillDatabase.Schema.create(driver)
   val store = LocalStore(BerillDatabase(driver), HybridClock("test") { 1L })
   var allowed = false
   val scheduler = object : ReminderScheduler {
    override suspend fun requestPermission() = allowed
    override fun replace(group: String, reminders: List<Reminder>) {}
    override fun cancel(id: String) {}
   }
   val reminders = ReminderCenter(store, scheduler)
   assertFailsWith<FeatureException> { reminders.setEnabled(true) }
   assertFalse(reminders.enabled.value)
   assertNull(store.meta("reminders.enabled"))
   allowed = true
   reminders.setEnabled(true)
   assertTrue(reminders.enabled.value)
   assertEquals("1", store.meta("reminders.enabled"))
  } finally { driver.close() }
 }
 @Test fun allFailureReasonsAndOperationsHaveBothTranslations() {
  for (language in listOf("ru", "en")) {
   for (reason in FailureReason.entries) {
    val key = "errors.reason.${reason.name}"
    assertNotEquals(key, Tr(key).get(language))
   }
   for (operation in FailureOperation.entries) {
    val key = "errors.operation.${operation.name}"
    assertNotEquals(key, Tr(key).get(language))
   }
  }
 }
}
