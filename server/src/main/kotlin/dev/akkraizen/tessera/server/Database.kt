package dev.akkraizen.tessera.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database as ExposedDatabase

object Records : Table("records") {
  val userId = varchar("user_id", 64)
  val collection = varchar("collection", 64)
  val recordId = varchar("record_id", 256)
  val realms = text("realms")
  val clocks = text("clocks")
  val deletedAt = varchar("deleted_at", 64).nullable()
  val rev = long("rev")
  override val primaryKey = PrimaryKey(userId, collection, recordId)

  init {
    index(false, userId, rev)
  }
}

object Counters : Table("counters") {
  val userId = varchar("user_id", 64)
  val rev = long("rev")
  override val primaryKey = PrimaryKey(userId)
}

object ServerDatabase {
  fun connect(url: String, user: String, password: String): ExposedDatabase {
    val dataSource = HikariDataSource(
      HikariConfig().apply {
        jdbcUrl = url
        username = user
        this.password = password
        maximumPoolSize = 5
      }
    )
    val db = ExposedDatabase.connect(dataSource)
    transaction(db) {
      SchemaUtils.create(Records, Counters, Tokens)
    }
    return db
  }

  fun fromEnv(): ExposedDatabase =
    connect(
      url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/tessera",
      user = System.getenv("DATABASE_USER") ?: "tessera",
      password = System.getenv("DATABASE_PASSWORD") ?: throw IllegalStateException("No database password."),
    )
}
