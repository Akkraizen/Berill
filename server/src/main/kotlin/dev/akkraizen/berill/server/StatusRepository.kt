package dev.akkraizen.berill.server

import dev.akkraizen.berill.protocol.ClientInfo
import dev.akkraizen.berill.protocol.DeviceStatus
import dev.akkraizen.berill.protocol.ServerStatus
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class StatusRepository(private val db: Database) {

  fun touch(user: String, deviceId: String, client: ClientInfo?, now: Long = System.currentTimeMillis()) {
    val id = deviceId.take(64)
    transaction(db) {
      val updated = Devices.update({ (Devices.userId eq user) and (Devices.deviceId eq id) }) {
        it[lastSync] = now
        if (client != null) {
          it[name] = client.name.take(128)
          it[platform] = client.platform.take(32)
          it[version] = client.version.take(64)
        }
      }
      if (updated == 0) {
        Devices.insert {
          it[userId] = user
          it[Devices.deviceId] = id
          it[name] = client?.name?.take(128)
          it[platform] = client?.platform?.take(32)
          it[version] = client?.version?.take(64)
          it[firstSeen] = now
          it[lastSync] = now
        }
      }
    }
  }

  fun status(user: String): ServerStatus =
    transaction(db) {
      val total = Records.selectAll().where { Records.userId eq user }.count()
      val deleted = Records.selectAll()
        .where { (Records.userId eq user) and Records.deletedAt.isNotNull() }
        .count()
      val devices = Devices.selectAll()
        .where { Devices.userId eq user }
        .orderBy(Devices.lastSync, SortOrder.DESC)
        .map {
          DeviceStatus(
            deviceId = it[Devices.deviceId],
            name = it[Devices.name],
            platform = it[Devices.platform],
            version = it[Devices.version],
            firstSeen = it[Devices.firstSeen],
            lastSync = it[Devices.lastSync]
          )
        }
      ServerStatus(
        serverVersion = ServerVersion.value,
        user = user,
        records = total - deleted,
        deleted = deleted,
        devices = devices
      )
    }
}

object ServerVersion {
  val value: String by lazy {
    ServerVersion::class.java.getResourceAsStream("/berill-server-version.txt")
      ?.use { it.readBytes().decodeToString().trim() }
      ?.takeIf { it.isNotEmpty() }
      ?: "dev"
  }
}
