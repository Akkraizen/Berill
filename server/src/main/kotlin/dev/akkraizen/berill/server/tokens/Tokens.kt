package dev.akkraizen.berill.server.tokens

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

object Tokens : Table("tokens") {
  val id = varchar("id", 16)
  val userId = varchar("user_id", 64)
  val name = varchar("name", 64)
  val hash = varchar("hash", 64).uniqueIndex()
  val createdAt = long("created_at")
  val lastUsedAt = long("last_used_at").nullable()
  val revokedAt = long("revoked_at").nullable()
  override val primaryKey = PrimaryKey(id)
}

data class TokenInfo(
  val id: String,
  val user: String,
  val name: String,
  val createdAt: Long,
  val lastUsedAt: Long?,
  val revokedAt: Long?
)

object TokenCodec {
  const val PREFIX = "tsr_"
  private val random = SecureRandom()
  private const val ID_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

  fun generate(): String {
    val bytes = ByteArray(32).also(random::nextBytes)
    return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }

  fun hash(token: String): String =
    MessageDigest.getInstance("SHA-256").digest(token.trim().toByteArray()).joinToString("") {
      "%02x".format(it)
    }

  fun newId(): String = (1..8).map { ID_ALPHABET[random.nextInt(ID_ALPHABET.length)] }.joinToString("")

  val USER = Regex("[A-Za-z0-9_.-]{1,64}")
}

class TokenRepository(private val db: Database, private val now: () -> Long = System::currentTimeMillis) {
  private val touched = ConcurrentHashMap<String, Long>()

  fun create(user: String, name: String): Pair<TokenInfo, String> {
    require(TokenCodec.USER.matches(user)) {
      "имя пользователя: латиница, цифры, _ . - (до 64 символов)"
    }

    val token = TokenCodec.generate()
    val id = TokenCodec.newId()
    val created = now()
    val label = name.trim().ifEmpty { "device" }.take(64)

    transaction(db) {
      Tokens.insert {
        it[Tokens.id] = id
        it[userId] = user
        it[Tokens.name] = label
        it[hash] = TokenCodec.hash(token)
        it[createdAt] = created
      }
    }

    return TokenInfo(id, user, label, created, null, null) to token
  }

  fun list(user: String? = null): List<TokenInfo> =
    transaction(db) {
      val query = Tokens.selectAll()
      if (user != null)
        query.where { Tokens.userId eq user }

      query
        .orderBy(Tokens.userId to SortOrder.ASC, Tokens.createdAt to SortOrder.ASC)
        .map(::toInfo)
    }

  fun revoke(id: String): Boolean =
    transaction(db) {
      Tokens.update({ (Tokens.id eq id) and Tokens.revokedAt.isNull() }) {
        it[revokedAt] = now()
      } > 0
    }

  fun authenticate(token: String): String? {
    if (!token.startsWith(TokenCodec.PREFIX)) return null
    val hash = TokenCodec.hash(token)
    val row = transaction(db) {
      Tokens.selectAll()
        .where { (Tokens.hash eq hash) and Tokens.revokedAt.isNull() }
        .singleOrNull()
    } ?: return null

    val id = row[Tokens.id]
    val t = now()

    if (t - (touched[id] ?: 0L) > TOUCH_INTERVAL_MS) {
      touched[id] = t
      runCatching {
        transaction(db) {
          Tokens.update({ Tokens.id eq id }) {
            it[lastUsedAt] = t
          }
        }
      }
    }

    return row[Tokens.userId]
  }

  fun hasActiveTokens(): Boolean =
    transaction(db) {
      Tokens.selectAll().where {
        Tokens.revokedAt.isNull()
      }.limit(1).any()
    }

  private fun toInfo(row: ResultRow) =
    TokenInfo(
      id = row[Tokens.id],
      user = row[Tokens.userId],
      name = row[Tokens.name],
      createdAt = row[Tokens.createdAt],
      lastUsedAt = row[Tokens.lastUsedAt],
      revokedAt = row[Tokens.revokedAt]
    )

  private companion object {
    const val TOUCH_INTERVAL_MS = 10 * 60 * 1000L
  }
}
