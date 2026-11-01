package dev.akkraizen.berill.core.attachments

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

actual fun attachmentHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

class FileAttachmentCache(private val directory: File) : AttachmentCache {
  init { check(directory.isDirectory || directory.mkdirs()) }
  private fun file(hash: String): File { require(hash.matches(Regex("[0-9a-f]{64}"))); return File(directory, hash) }
  @Synchronized override fun put(hash: String, bytes: ByteArray) {
    require(attachmentHash(bytes) == hash)
    val target = file(hash)
    if (target.isFile && attachmentHash(target.readBytes()) == hash) return
    val temporary = File.createTempFile("attachment-", ".tmp", directory)
    try { temporary.writeBytes(bytes); Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    finally { temporary.delete() }
  }
  override fun get(hash: String): ByteArray? = file(hash).takeIf { it.isFile }?.readBytes()?.takeIf { attachmentHash(it) == hash }
  override fun hashes(): List<String> = directory.listFiles().orEmpty().filter { it.isFile && it.name.matches(Regex("[0-9a-f]{64}")) }.map { it.name }
}
