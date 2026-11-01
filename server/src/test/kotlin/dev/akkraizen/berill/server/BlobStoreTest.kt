package dev.akkraizen.berill.server

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import kotlin.test.*
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.S3Exception

class BlobStoreTest {
  @Test fun signedS3RequestsKeepPrivatePrefixAndPropagateErrors() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
    var mode = "ok"
    server.createContext("/") { call ->
      requests += "${call.requestMethod} ${call.requestURI.path}"
      assertTrue(call.requestHeaders.getFirst("Authorization").startsWith("AWS4-HMAC-SHA256 "))
      call.requestBody.use { it.readBytes() }
      if (mode == "ok") {
        val bytes = if (call.requestMethod == "GET") "encrypted-photo".encodeToByteArray() else byteArrayOf()
        call.sendResponseHeaders(200, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        call.responseBody.use { it.write(bytes) }
      } else {
        val bytes = "<Error><Code>$mode</Code><Message>test</Message></Error>".encodeToByteArray()
        call.responseHeaders.set("Content-Type", "application/xml")
        call.sendResponseHeaders(if (mode == "AccessDenied") 403 else 404, bytes.size.toLong())
        call.responseBody.use { it.write(bytes) }
      }
      call.close()
    }
    server.start()
    val client = S3Client.builder().region(Region.US_EAST_1).forcePathStyle(true)
      .httpClientBuilder(UrlConnectionHttpClient.builder())
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test-secret")))
      .endpointOverride(URI("http://127.0.0.1:${server.address.port}" )).build()
    try {
      val store = S3BlobStore(client, "private-bucket", "/berill/attachments/")
      val key = "${"a".repeat(64)}/${"b".repeat(64)}"
      store.put(key, "encrypted-photo".encodeToByteArray())
      assertEquals("encrypted-photo", store.get(key)!!.decodeToString())
      assertEquals(listOf("PUT /private-bucket/berill/attachments/$key", "GET /private-bucket/berill/attachments/$key"), requests.toList())
      mode = "NoSuchKey"
      assertNull(store.get(key))
      mode = "NoSuchBucket"
      assertFailsWith<S3Exception> { store.get(key) }
      mode = "AccessDenied"
      assertFailsWith<S3Exception> { store.get(key) }
    } finally { client.close(); server.stop(0) }
  }

  @Test fun migrationReadsLegacyWithoutDeletingAndNeverHidesRemoteFailures() {
    val directory = Files.createTempDirectory("berill-legacy").toFile()
    val uploaded = mutableMapOf<String, ByteArray>()
    var fail = false
    val remote = object : BlobStore {
      override fun get(key: String): ByteArray? { check(!fail); return uploaded[key] }
      override fun put(key: String, bytes: ByteArray) { check(!fail); uploaded[key] = bytes }
    }
    try {
      val local = FileBlobStore(directory)
      local.put("user/hash", "legacy".encodeToByteArray())
      val store = MigratingBlobStore(remote, local)
      assertEquals("legacy", store.get("user/hash")!!.decodeToString())
      store.put("user/hash", "new".encodeToByteArray())
      assertEquals("new", store.get("user/hash")!!.decodeToString())
      assertEquals("legacy", local.get("user/hash")!!.decodeToString())
      fail = true
      assertFailsWith<IllegalStateException> { store.get("user/hash") }
      assertFailsWith<IllegalStateException> { store.put("user/other", byteArrayOf(1)) }
      assertNull(local.get("user/other"))
    } finally { directory.deleteRecursively() }
  }

  @Test fun invalidStorageConfigurationFailsBeforeServingRequests() {
    assertFailsWith<IllegalStateException> { blobStoreFromEnv(mapOf("ATTACHMENTS_BACKEND" to "typo")) }
    assertFailsWith<IllegalArgumentException> { blobStoreFromEnv(mapOf("ATTACHMENTS_BACKEND" to "s3")) }
    assertFailsWith<IllegalArgumentException> {
      blobStoreFromEnv(mapOf("ATTACHMENTS_BACKEND" to "s3", "ATTACHMENTS_S3_BUCKET" to "bucket", "ATTACHMENTS_S3_ENDPOINT" to "file:///tmp/data"))
    }
  }
}
