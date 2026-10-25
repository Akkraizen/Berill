package dev.akkraizen.berill.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

interface HttpAccess {
  suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement
}

class HttpException(val status: Int, message: String = "HTTP $status") : Exception(message)

fun String.urlEncode(): String =
  buildString {
    for (b in this@urlEncode.encodeToByteArray()) {
      val c = b.toInt() and 0xFF
      val ch = c.toChar()
      if (ch in 'A'..'Z' ||
        ch in 'a'..'z' ||
        ch in '0'..'9' ||
        ch == '-' ||
        ch == '_' ||
        ch == '.' ||
        ch == '~'
      ) {
        append(ch)
      } else {
        append('%')
        append("0123456789ABCDEF"[c shr 4])
        append("0123456789ABCDEF"[c and 0x0F])
      }
    }
  }

fun JsonElement?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

fun JsonElement?.arr(key: String): List<JsonElement> = ((this as? JsonObject)?.get(key) as? JsonArray).orEmpty()

fun JsonElement?.str(key: String): String? =
  ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf {
    it.isNotEmpty()
  }

fun JsonElement?.int(key: String): Int? =
  ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.let {
    it.intOrNull ?: it.doubleOrNull?.toInt()
  }

fun JsonElement?.double(key: String): Double? =
  ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.let {
    it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull()
  }

fun JsonElement?.list(): List<JsonElement> = (this as? JsonArray).orEmpty()

fun JsonElement?.strings(key: String): List<String> =
  arr(key).mapNotNull {
    (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
  }

fun String.stripHtml(): String =
  replace(Regex("<br\\s*/?>|</p>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&quot;", "\"")
    .replace("&amp;", "&")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")
    .lines()
    .joinToString("\n") { it.trim() }
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
