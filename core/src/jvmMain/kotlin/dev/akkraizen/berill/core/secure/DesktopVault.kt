package dev.akkraizen.berill.core.secure

import dev.akkraizen.berill.core.previousVaultEntropy
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64

class DesktopVault(private val dir: File) : SecretVault {
  private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

  init {
    dir.mkdirs()
    if (!windows)
      runCatching {
        Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwx------"))
      }
  }

  private fun file(name: String): File {
    require(Regex("[A-Za-z0-9_.-]{1,128}").matches(name)) { "bad secret name" }
    return File(dir, name)
  }

  override fun read(name: String): String? {
    val f = file(name)
    if (!f.isFile) return null
    val bytes = runCatching { Base64.getDecoder().decode(f.readText().trim()) }.getOrNull() ?: return null
    val plain = if (windows) Dpapi.unprotect(bytes) else bytes
    return plain?.decodeToString()
  }

  override fun write(name: String, value: String?) {
    val f = file(name)
    if (value.isNullOrEmpty()) {
      f.delete()
      return
    }
    val bytes = value.encodeToByteArray()
    val stored = if (windows) Dpapi.protect(bytes) ?: error("DPAPI failed") else bytes
    f.writeText(Base64.getEncoder().encodeToString(stored))
    if (!windows)
      runCatching {
        Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------"))
      }
  }
}

internal object Dpapi {
  @Structure.FieldOrder("cbData", "pbData")
  class Blob : Structure() {
    @JvmField
    var cbData: Int = 0

    @JvmField
    var pbData: Pointer? = null
  }

  @Suppress("FunctionName")
  private interface Crypt32 : StdCallLibrary {
    fun CryptProtectData(
      dataIn: Blob,
      description: WString?,
      entropy: Blob?,
      reserved: Pointer?,
      prompt: Pointer?,
      flags: Int,
      dataOut: Blob
    ): Boolean

    fun CryptUnprotectData(
      dataIn: Blob,
      description: Pointer?,
      entropy: Blob?,
      reserved: Pointer?,
      prompt: Pointer?,
      flags: Int,
      dataOut: Blob
    ): Boolean
  }

  @Suppress("FunctionName")
  private interface Kernel32 : StdCallLibrary {
    fun LocalFree(memory: Pointer?): Pointer?
  }

  private const val CRYPTPROTECT_UI_FORBIDDEN = 0x1
  private val crypt32 by lazy { Native.load("Crypt32", Crypt32::class.java) }
  private val kernel32 by lazy { Native.load("Kernel32", Kernel32::class.java) }

  private val entropyBytes = "Berill secret vault v1".encodeToByteArray()

  private fun blob(bytes: ByteArray): Blob {
    val memory = Memory(maxOf(1, bytes.size).toLong())
    memory.write(0, bytes, 0, bytes.size)
    return Blob().apply {
      cbData = bytes.size
      pbData = memory
    }
  }

  fun protect(data: ByteArray, entropyValue: ByteArray = entropyBytes): ByteArray? =
    call(entropyValue) { input, entropy, output ->
      crypt32.CryptProtectData(
        input,
        WString("Berill"),
        entropy,
        null,
        null,
        CRYPTPROTECT_UI_FORBIDDEN,
        output
      )
    }(data)

  fun unprotect(data: ByteArray): ByteArray? =
    unprotect(data, entropyBytes) ?: unprotect(data, previousVaultEntropy())

  private fun unprotect(data: ByteArray, entropyValue: ByteArray): ByteArray? =
    call(entropyValue) { input, entropy, output ->
      crypt32.CryptUnprotectData(
        input,
        null,
        entropy,
        null,
        null,
        CRYPTPROTECT_UI_FORBIDDEN,
        output
      )
    }(data)

  private fun call(entropyValue: ByteArray, op: (Blob, Blob, Blob) -> Boolean): (ByteArray) -> ByteArray? =
    { data ->
      runCatching {
        val input = blob(data)
        val entropy = blob(entropyValue)
        val output = Blob()
        if (!op(input, entropy, output)) return@runCatching null
        output.read()
        val result = output.pbData?.getByteArray(0, output.cbData)
        kernel32.LocalFree(output.pbData)
        result
      }
        .getOrNull()
    }
}
