package dev.akkraizen.berill.core.attachments

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

actual fun attachmentHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

class FileAttachmentCache(private val directory: File, private val memoryLimitBytes: Int = 24 * 1024 * 1024) : AttachmentCache {
  private val memory = LinkedHashMap<String, ByteArray>(16, 0.75f, true)
  private var memoryBytes = 0
  private fun remember(hash: String, bytes: ByteArray) {
    memory.remove(hash)?.let { memoryBytes -= it.size }
    if (bytes.size > memoryLimitBytes) return
    while (memoryBytes + bytes.size > memoryLimitBytes && memory.isNotEmpty()) {
      val oldest = memory.entries.iterator().next()
      memoryBytes -= oldest.value.size
      memory.remove(oldest.key)
    }
    memory[hash] = bytes.copyOf()
    memoryBytes += bytes.size
  }
  init { check(directory.isDirectory || directory.mkdirs()) }
  private fun file(hash: String): File { require(hash.matches(Regex("[0-9a-f]{64}"))); return File(directory, hash) }
  @Synchronized override fun put(hash: String, bytes: ByteArray) {
    require(attachmentHash(bytes) == hash)
    val target = file(hash)
    if (target.isFile && attachmentHash(target.readBytes()) == hash) { remember(hash, bytes); return }
    val temporary = File.createTempFile("attachment-", ".tmp", directory)
    try { temporary.writeBytes(bytes); Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    finally { temporary.delete() }
    remember(hash, bytes)
  }
  @Synchronized override fun get(hash: String): ByteArray? {
    val target = file(hash)
    memory[hash]?.let { return it.copyOf() }
    val bytes = target.takeIf { it.isFile }?.readBytes()?.takeIf { attachmentHash(it) == hash } ?: return null
    target.setLastModified(System.currentTimeMillis()) // last use, for trim()
    remember(hash, bytes)
    return bytes
  }

  @Synchronized override fun size(): Long = files().sumOf { it.length() }

  @Synchronized override fun trim(maxBytes: Long, keep: Set<String>): Int {
    val files = files().sortedBy { it.lastModified() }
    var total = files.sumOf { it.length() }
    var removed = 0
    for (file in files) {
      if (total <= maxBytes) break
      if (file.name in keep) continue
      val length = file.length()
      if (file.delete()) {
        total -= length
        removed++
        memory.remove(file.name)?.let { memoryBytes -= it.size }
      }
    }
    return removed
  }

  private fun files(): List<File> = directory.listFiles().orEmpty().filter { it.isFile && it.name.matches(HASH) }
  override fun hashes(): List<String> = files().map { it.name }

  private companion object { val HASH = Regex("[0-9a-f]{64}") }
}
