package dev.akkraizen.berill.core.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.locks.ReentrantLock

class SharedSqliteDriver(url: String, properties: Properties) : JdbcDriver() {
  private val connection: Connection = DriverManager.getConnection(url, properties)
  private val lock = ReentrantLock()
  private val listeners = linkedMapOf<String, MutableSet<Query.Listener>>()

  override fun getConnection(): Connection {
    lock.lock()
    return connection
  }

  override fun closeConnection(connection: Connection) {
    if (lock.isHeldByCurrentThread) lock.unlock()
  }

  override fun Connection.beginTransaction() {
    try {
      check(autoCommit) { "A transaction is already open on the shared connection" }
      autoCommit = false
    } catch (e: Throwable) {
      closeConnection(this)
      throw e
    }
  }

  override fun close() {
    lock.lock()
    try { connection.close() } finally { lock.unlock() }
  }

  override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
    synchronized(listeners) { queryKeys.forEach { listeners.getOrPut(it) { linkedSetOf() }.add(listener) } }
  }

  override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
    synchronized(listeners) { queryKeys.forEach { listeners[it]?.remove(listener) } }
  }

  override fun notifyListeners(vararg queryKeys: String) {
    val targets = linkedSetOf<Query.Listener>()
    synchronized(listeners) { queryKeys.forEach { listeners[it]?.let(targets::addAll) } }
    targets.forEach(Query.Listener::queryResultsChanged)
  }

  companion object {
    fun open(file: File, schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver {
      val driver = SharedSqliteDriver("jdbc:sqlite:${file.absolutePath}", properties())
      object : TransacterImpl(driver) {}.transaction {
        val version = driver.executeQuery(null, "PRAGMA user_version", { cursor: SqlCursor ->
          QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }, 0, null).value ?: 0L
        if (version == 0L) {
          schema.create(driver).value
          driver.execute(null, "PRAGMA user_version = ${schema.version}", 0, null).value
        } else if (version < schema.version) {
          schema.migrate(driver, version, schema.version).value
          driver.execute(null, "PRAGMA user_version = ${schema.version}", 0, null).value
        }
      }
      return driver
    }

    fun properties(): Properties = Properties().apply {
      setProperty("journal_mode", "WAL")
      setProperty("busy_timeout", "15000")
      setProperty("transaction_mode", "IMMEDIATE")
    }
  }
}
