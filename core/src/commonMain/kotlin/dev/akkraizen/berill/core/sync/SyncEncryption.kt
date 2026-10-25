package dev.akkraizen.berill.core.sync

import dev.akkraizen.berill.core.previousInstallationName
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.secure.CryptoPrimitives
import dev.akkraizen.berill.core.secure.SecretVault
import dev.akkraizen.berill.core.secure.platformCrypto
import dev.akkraizen.berill.protocol.Collections
import dev.akkraizen.berill.protocol.SyncRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

enum class E2eState {

  Off,
  Locked,
  On,
}

@OptIn(ExperimentalEncodingApi::class)
class SyncEncryption(
  private val store: LocalStore,
  private val vault: SecretVault,
  private val crypto: CryptoPrimitives = platformCrypto()
) {
  private val json = Json
  private var key: ByteArray? = runCatching { vault.read(VAULT_KEY)?.let { Base64.decode(it) } }.getOrNull()
  private val _state = MutableStateFlow(computeState())
  val state: StateFlow<E2eState> = _state

  private fun params(): JsonObject? =
    store
      .get(Collections.SETTINGS, RECORD_ID)
      ?.takeUnless { it.deleted }
      ?.fields
      ?.takeIf { it["salt"] != null }

  private fun computeState(): E2eState =
    when {
      params() == null -> E2eState.Off
      key == null -> E2eState.Locked
      else -> E2eState.On
    }

  fun refresh() {
    _state.value = computeState()
  }

  fun enable(password: String) {
    require(password.length >= MIN_PASSWORD) { "short password" }
    val salt = crypto.randomBytes(16)
    val newKey = crypto.pbkdf2(password, salt, ITERATIONS, 32)
    val check = Base64.encode(crypto.encrypt(newKey, CHECK_TEXT.encodeToByteArray(), CHECK_AAD))
    saveKey(newKey)
    store.put(
      Collections.SETTINGS,
      RECORD_ID,
      mapOf(
        "salt" to JsonPrimitive(Base64.encode(salt)),
        "iterations" to JsonPrimitive(ITERATIONS),
        "check" to JsonPrimitive(check)
      )
    )
    store.restampAll()
    refresh()
  }

  fun unlock(password: String): Boolean {
    val p = params() ?: return false
    val salt = p.string("salt")?.let { runCatching { Base64.decode(it) }.getOrNull() } ?: return false
    val iterations = (p["iterations"] as? JsonPrimitive)?.intOrNull ?: ITERATIONS
    val check = p.string("check")?.let { runCatching { Base64.decode(it) }.getOrNull() } ?: return false
    val candidate = crypto.pbkdf2(password, salt, iterations, 32)
    val plain = crypto.decrypt(candidate, check, CHECK_AAD)
    val validCurrent = plain?.decodeToString() == CHECK_TEXT
    val previousName = previousInstallationName()
    val validPrevious = !validCurrent && crypto.decrypt(candidate, check,
      "$previousName/e2e/check".encodeToByteArray())?.decodeToString() == "$previousName-e2e-check"
    if (!validCurrent && !validPrevious) return false
    saveKey(candidate)
    refresh()
    return true
  }

  fun forgetKey() {
    key = null
    vault.write(VAULT_KEY, null)
    refresh()
  }

  private fun saveKey(value: ByteArray) {
    key = value
    vault.write(VAULT_KEY, Base64.encode(value))
  }

  private fun isParams(record: SyncRecord) = record.collection == Collections.SETTINGS && record.id == RECORD_ID

  fun encryptOutgoing(record: SyncRecord): SyncRecord? {
    if (isParams(record)) return record
    return when (_state.value) {
      E2eState.Off -> record
      E2eState.Locked -> null
      E2eState.On -> {
        val k = key ?: return null
        record.copy(
          fields = JsonObject(
            record.fields.mapValues { (field, value) ->
              encryptValue(k, record, field, value)
            }
          )
        )
      }
    }
  }

  fun decryptIncoming(record: SyncRecord): SyncRecord? {
    if (isParams(record)) return record
    val encrypted = record.fields.values.any { it.isEncrypted() }
    if (!encrypted) return record
    val k = key ?: return null
    val fields = LinkedHashMap<String, JsonElement>()
    for ((field, value) in record.fields) {
      fields[field] = if (value.isEncrypted()) decryptValue(k, record, field, value) ?: return null else value
    }
    return record.copy(fields = JsonObject(fields))
  }

  private fun aad(record: SyncRecord, field: String) = "${record.collection}/${record.id}/$field".encodeToByteArray()

  private fun encryptValue(k: ByteArray, record: SyncRecord, field: String, value: JsonElement): JsonElement {
    if (value.isEncrypted()) return value
    val data = crypto.encrypt(k, value.toString().encodeToByteArray(), aad(record, field))
    return JsonPrimitive(PREFIX + Base64.encode(data))
  }

  private fun decryptValue(k: ByteArray, record: SyncRecord, field: String, value: JsonElement): JsonElement? {
    val text = (value as JsonPrimitive).content.removePrefix(PREFIX)
    val data = runCatching { Base64.decode(text) }.getOrNull() ?: return null
    val plain = crypto.decrypt(k, data, aad(record, field)) ?: return null
    return runCatching { json.parseToJsonElement(plain.decodeToString()) }.getOrNull()
  }

  private fun JsonElement.isEncrypted(): Boolean = this is JsonPrimitive && isString && content.startsWith(PREFIX)

  private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

  companion object {
    const val RECORD_ID = "e2e"
    const val VAULT_KEY = "sync.e2e.key"
    const val MIN_PASSWORD = 8
    private const val PREFIX = "e1:"
    private const val ITERATIONS = 310_000
    private const val CHECK_TEXT = "berill-e2e-check"
    private val CHECK_AAD = "berill/e2e/check".encodeToByteArray()
  }
}
