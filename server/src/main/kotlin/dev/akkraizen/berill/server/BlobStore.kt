package dev.akkraizen.berill.server

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.S3Exception

interface BlobStore : AutoCloseable {
  fun get(key: String): ByteArray?
  fun put(key: String, bytes: ByteArray)
  fun exists(key: String): Boolean = get(key) != null
  override fun close() {}
}

class FileBlobStore(private val root: File) : BlobStore {
  override fun get(key: String): ByteArray? = File(root, key).takeIf { it.isFile }?.readBytes()
  override fun exists(key: String): Boolean = File(root, key).isFile
  override fun put(key: String, bytes: ByteArray) {
    val target = File(root, key)
    check(target.parentFile.isDirectory || target.parentFile.mkdirs())
    val temp = File.createTempFile("upload-", ".tmp", target.parentFile)
    try {
      temp.writeBytes(bytes)
      Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } finally { temp.delete() }
  }
}

class S3BlobStore(
  private val client: S3Client,
  private val bucket: String,
  prefix: String,
) : BlobStore {
  private val prefix = prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
  override fun get(key: String): ByteArray? = try {
    client.getObjectAsBytes { it.bucket(bucket).key(prefix + key) }.asByteArray()
  } catch (e: S3Exception) {
    // Missing buckets and denied credentials must never look like missing attachments.
    if (e.statusCode() == 404 && e.awsErrorDetails()?.errorCode() == "NoSuchKey") null else throw e
  }
  override fun exists(key: String): Boolean = try {
    client.headObject { it.bucket(bucket).key(prefix + key) }
    true
  } catch (e: S3Exception) {
    if (e.statusCode() == 404) false else throw e
  }
  override fun put(key: String, bytes: ByteArray) {
    client.putObject({ it.bucket(bucket).key(prefix + key).contentType("application/octet-stream") }, RequestBody.fromBytes(bytes))
  }
  override fun close() = client.close()
}

// Existing files remain readable during migration. Upload failures propagate; no silent local-only writes.
class MigratingBlobStore(private val remote: BlobStore, private val legacy: BlobStore) : BlobStore {
  override fun get(key: String): ByteArray? = remote.get(key) ?: legacy.get(key)
  override fun exists(key: String): Boolean = remote.exists(key) || legacy.exists(key)
  override fun put(key: String, bytes: ByteArray) = remote.put(key, bytes)
  override fun close() { try { remote.close() } finally { legacy.close() } }
}

fun blobStoreFromEnv(env: Map<String, String> = System.getenv()): BlobStore {
  val local = FileBlobStore(File(env["ATTACHMENTS_PATH"] ?: "/app/attachments"))
  return when (env["ATTACHMENTS_BACKEND"].orEmpty().ifBlank { "local" }) {
    "local" -> local
    "s3" -> {
      val bucket = requireNotNull(env["ATTACHMENTS_S3_BUCKET"]?.takeIf { it.isNotBlank() }) { "ATTACHMENTS_S3_BUCKET is required" }
      val builder = S3Client.builder()
        .region(Region.of(env["AWS_REGION"].orEmpty().ifBlank { "us-east-1" }))
        .forcePathStyle(env["ATTACHMENTS_S3_PATH_STYLE"].orEmpty().ifBlank { "true" }.toBooleanStrict())
        .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(10)).socketTimeout(Duration.ofSeconds(60)))
        .overrideConfiguration { it.apiCallTimeout(Duration.ofSeconds(90)).apiCallAttemptTimeout(Duration.ofSeconds(65)) }
      env["ATTACHMENTS_S3_ENDPOINT"]?.takeIf { it.isNotBlank() }?.let { value ->
        val uri = URI.create(value)
        require(uri.scheme in setOf("https", "http") && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Invalid S3 endpoint" }
        builder.endpointOverride(uri)
      }
      MigratingBlobStore(S3BlobStore(builder.build(), bucket, env["ATTACHMENTS_S3_PREFIX"] ?: "attachments"), local)
    }
    else -> error("ATTACHMENTS_BACKEND must be local or s3")
  }
}
