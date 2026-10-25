package dev.akkraizen.berill.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SyncRecord(
  val collection: String,
  val id: String,
  val fields: JsonObject = JsonObject(emptyMap()),
  val clocks: Map<String, String> = emptyMap(),
  val deletedAt: String? = null,
  val rev: Long = 0
) {
  val deleted: Boolean
    get() {
      val del = deletedAt
        ?: return false
      val latestField = clocks.values.maxOrNull()
        ?: return true
      return del >= latestField
    }

  val latestClock: String?
    get() = (clocks.values + listOfNotNull(deletedAt)).maxOrNull()

  fun mergeWith(other: SyncRecord): SyncRecord {
    require(collection == other.collection && id == other.id) {
      "You can only merge versions of a single record"
    }

    val mergedFields = LinkedHashMap<String, JsonElement>()
    val mergedClocks = LinkedHashMap<String, String>()

    for (key in (clocks.keys + other.clocks.keys)) {
      val mine = clocks[key]
      val theirs = other.clocks[key]
      val takeTheirs =
        shouldTakeTheirs(mine = mine, theirs = theirs, myField = fields[key], theirField = other.fields[key])

      if (takeTheirs) {
        other.fields[key]?.let { mergedFields[key] = it }
        mergedClocks[key] = theirs!!
      } else if (mine != null) {
        fields[key]?.let { mergedFields[key] = it }
        mergedClocks[key] = mine
      }
    }

    val del = listOfNotNull(deletedAt, other.deletedAt).maxOrNull()
    return SyncRecord(
      collection = collection,
      id = id,
      fields = JsonObject(mergedFields),
      clocks = mergedClocks,
      deletedAt = del,
      rev = maxOf(rev, other.rev)
    )
  }

  private fun shouldTakeTheirs(
    mine: String?,
    theirs: String?,
    myField: JsonElement?,
    theirField: JsonElement?
  ): Boolean {
    if (theirs == null)
      return false

    if (mine == null)
      return true

    return theirs > mine
      ||
      (
        theirs == mine
          &&
          (theirField?.toString().orEmpty() > myField?.toString().orEmpty())
      )
  }

  fun sameContentAs(other: SyncRecord?): Boolean =
    other != null &&
      fields == other.fields &&
      clocks == other.clocks &&
      deletedAt == other.deletedAt
}
