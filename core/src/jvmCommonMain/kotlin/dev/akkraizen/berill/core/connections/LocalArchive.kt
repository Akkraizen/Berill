package dev.akkraizen.berill.core.connections

import dev.akkraizen.berill.protocol.SyncRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.*

private const val LIMIT = 256 * 1024 * 1024

actual fun encodeLocalArchive(records: List<SyncRecord>, attachments: Map<String, ByteArray>): ByteArray {
  val out = ByteArrayOutputStream()
  var expandedSize = 0L
  ZipOutputStream(out).use { zip ->
    fun entry(name: String, content: String) {
      val bytes = content.encodeToByteArray()
      expandedSize += bytes.size
      require(expandedSize <= LIMIT) { "Archive too large" }
      zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
    }
    entry("manifest.json", "{\"format\":\"berill-local\",\"version\":1}")
    entry("records.json", Json.encodeToString(records))
    attachments.forEach { (hash, bytes) ->
      require(dev.akkraizen.berill.core.attachments.attachmentHash(bytes) == hash)
      expandedSize += bytes.size
      require(expandedSize <= LIMIT) { "Archive too large" }
      zip.putNextEntry(ZipEntry("attachments/$hash")); zip.write(bytes); zip.closeEntry()
    }
    records.filter { it.collection == "notes" && !it.deleted }.forEach { note ->
      val safe = note.id.encodeToByteArray().joinToString("") { "%02x".format(it.toInt() and 255) }
      val title = (note.fields["title"] as? JsonPrimitive)?.content.orEmpty()
      val body = (note.fields["body"] as? JsonPrimitive)?.content.orEmpty()
      entry("notes/$safe.md", "# $title\n\n$body\n")
    }
  }
  return out.toByteArray()
}

actual fun decodeLocalArchive(bytes: ByteArray): List<SyncRecord> {
  require(bytes.size <= LIMIT) { "Archive too large" }
  var records: String? = null
  var version: Int? = null
  var total = 0L
  val names = mutableSetOf<String>()
  ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
    while (true) {
      val entry = zip.nextEntry ?: break
      require(names.add(entry.name) && names.size <= 100000) { "Duplicate entry or too many files" }
      require(!entry.name.startsWith('/') && !entry.name.contains('\\') && entry.name.split('/').none { it == ".." }) { "Invalid archive path" }
      val out = ByteArrayOutputStream()
      val buffer = ByteArray(8192)
      while (true) {
        val n = zip.read(buffer); if (n < 0) break
        total += n
        require(total <= LIMIT) { "Archive too large" }
        if (entry.name == "records.json" || entry.name == "manifest.json") out.write(buffer, 0, n)
      }
      if (entry.name == "records.json") records = out.toString("UTF-8")
      if (entry.name == "manifest.json") {
        val manifest = Json.parseToJsonElement(out.toString("UTF-8")).jsonObject
        require(manifest["format"]?.jsonPrimitive?.content == "berill-local")
        version = manifest["version"]?.jsonPrimitive?.intOrNull
      }
    }
  }
  require(version == 1) { "Unsupported archive version" }
  return Json.decodeFromString<List<SyncRecord>>(requireNotNull(records)).also { data ->
    require(data.map { it.collection to it.id }.toSet().size == data.size) { "Duplicate records" }
  }
}

actual fun decodeArchiveAttachments(bytes: ByteArray): Map<String, ByteArray> {
  decodeLocalArchive(bytes) // Validates total expansion size and paths before reading any blob.
  val blobs = mutableMapOf<String, ByteArray>()
  ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
    while (true) {
      val entry = zip.nextEntry ?: break
      if (!entry.name.startsWith("attachments/")) continue
      val hash = entry.name.removePrefix("attachments/")
      require(hash.matches(Regex("[0-9a-f]{64}")))
      val data = zip.readBytes()
      require(dev.akkraizen.berill.core.attachments.attachmentHash(data) == hash)
      blobs[hash] = data
    }
  }
  return blobs
}
