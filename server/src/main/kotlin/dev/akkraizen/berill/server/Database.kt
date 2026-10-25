package dev.akkraizen.berill.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.akkraizen.berill.server.tokens.Tokens
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

object Datasets : Table("datasets") {
  val userId = varchar("user_id", 64)
  val dataset = varchar("dataset", 36)
  override val primaryKey = PrimaryKey(userId)
}

object Devices : Table("devices") {
  val userId = varchar("user_id", 64)
  val deviceId = varchar("device_id", 64)
  val name = varchar("name", 128).nullable()
  val platform = varchar("platform", 32).nullable()
  val version = varchar("version", 64).nullable()
  val firstSeen = long("first_seen")
  val lastSync = long("last_sync")
  override val primaryKey = PrimaryKey(userId, deviceId)
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
      SchemaUtils.create(Records, Counters, Tokens, Datasets, Devices)
    }
    return db
  }

  fun fromEnv(): ExposedDatabase =
    connect(
      url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/berill",
      user = System.getenv("DATABASE_USER") ?: "berill",
      password = System.getenv("DATABASE_PASSWORD")
        ?: throw IllegalStateException("No database password.")
    )
}
