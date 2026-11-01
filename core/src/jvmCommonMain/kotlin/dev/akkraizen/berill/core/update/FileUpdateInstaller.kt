package dev.akkraizen.berill.core.update

import dev.akkraizen.berill.core.sync.createHttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*

abstract class FileUpdateInstaller(protected val directory: File) : UpdateInstaller {
  override val available get() = true
  /** Publisher key from the build; tests replace it. */
  protected open val publicKey: String get() = dev.akkraizen.berill.core.BuildInfo.UPDATE_PUBLIC_KEY
  protected fun packageFile(path: String): File {
    val file = File(path).canonicalFile
    if (file.parentFile != directory.canonicalFile || !file.name.startsWith("package-")) throw UpdateException(UpdateFailure.PACKAGE)
    return file
  }
  override suspend fun download(update: AppUpdate, progress: (Long, Long?) -> Unit): String = withContext(Dispatchers.IO) {
    val uri = update.url?.let(URI::create) ?: throw UpdateException(UpdateFailure.PACKAGE)
    if (uri.scheme != "https" && !(uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost", "::1", "[::1]"))) throw UpdateException(UpdateFailure.INSECURE)
    if (uri.userInfo != null || uri.fragment != null) throw UpdateException(UpdateFailure.INSECURE)
    val expectedHash = update.sha256?.takeIf { it.matches(Regex("[a-f0-9]{64}")) } ?: throw UpdateException(UpdateFailure.SERVER_TOO_OLD)
    val size = update.size?.takeIf { it in 1..MAX_BYTES } ?: throw UpdateException(UpdateFailure.PACKAGE)
    val filename = uri.path.substringAfterLast('/')
    if (!filename.startsWith("Berill-${update.version}-")) throw UpdateException(UpdateFailure.PACKAGE)
    if (!filename.matches(Regex("Berill-[0-9]+(?:\\.[0-9]+){2}-(?:windows\\.exe|Setup\\.exe|android\\.apk|macos\\.dmg|linux\\.deb|linux\\.rpm|windows\\.zip)"))) throw UpdateException(UpdateFailure.PACKAGE)
    requirePublisherSignature(publicKey, update, filename)
    check(directory.isDirectory || directory.mkdirs())
    val target = File(directory, "package-${UUID.randomUUID()}-${filename}")
    val partial = File(directory, target.name + ".part")
    val http = createHttpClient().config { followRedirects = false }
    try {
      val digest = MessageDigest.getInstance("SHA-256")
      var count = 0L
      http.prepareGet(uri.toString()) {
        timeout { requestTimeoutMillis = 60 * 60 * 1000; socketTimeoutMillis = 30_000 }
      }.execute { response ->
        if (!response.status.isSuccess()) throw UpdateException(UpdateFailure.DOWNLOAD)
        val declared = response.headers["Content-Length"]?.toLongOrNull()
        if (declared != null && declared != size) throw UpdateException(UpdateFailure.INTEGRITY)
        val input = response.bodyAsChannel()
        partial.outputStream().use { output ->
          val buffer = ByteArray(65536)
          while (true) {
            ensureActive()
            val n = input.readAvailable(buffer, 0, buffer.size)
            if (n < 0) break
            if (n == 0) continue
            count += n
            if (count > size || count > MAX_BYTES) throw UpdateException(UpdateFailure.INTEGRITY)
            output.write(buffer, 0, n); digest.update(buffer, 0, n)
            progress(count, size)
          }
        }
      }
      val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
      if (count != size || actual != expectedHash) throw UpdateException(UpdateFailure.INTEGRITY)
      ensureActive()
      check(partial.renameTo(target))
      target.absolutePath
    } finally { partial.delete(); http.close() }
  }
  override suspend fun validate(path: String, update: AppUpdate) = withContext(Dispatchers.IO) {
    val file = packageFile(path)
    if (!file.isFile || file.length() != update.size) throw UpdateException(UpdateFailure.INTEGRITY)
    val published = PACKAGE_NAME.matchEntire(file.name)?.groupValues?.get(1) ?: throw UpdateException(UpdateFailure.PACKAGE)
    requirePublisherSignature(publicKey, update, published)
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(65536)
      while (true) { ensureActive(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
    }
    val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    if (hash != update.sha256) throw UpdateException(UpdateFailure.INTEGRITY)
  }
  override suspend fun remove(path: String) { withContext(Dispatchers.IO) { packageFile(path).delete() }; Unit }
  companion object {
    const val MAX_BYTES = 1024L * 1024 * 1024
    /** `package-<uuid>-<published file name>`, as written by [download]. */
    private val PACKAGE_NAME = Regex("package-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}-(Berill-.+)")
  }
}
