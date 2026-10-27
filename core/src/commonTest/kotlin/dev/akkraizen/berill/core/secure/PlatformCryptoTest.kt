package dev.akkraizen.berill.core.secure

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class PlatformCryptoTest {
  private val crypto = platformCrypto()

  @Test
  fun pbkdf2MatchesReferenceVector() {
    val key = crypto.pbkdf2("correct horse", "berill-salt".encodeToByteArray(), 1000, 32)
    assertEquals("1b5a6fa946855545f4f2d247dccc7249b037fe0414c088bce3ffae1ffda10582", key.hex())
  }

  @Test
  fun decryptsReferenceCiphertext() {
    val key = ByteArray(32) { it.toByte() }
    val data = "000102030405060708090a0b0567a472a989e268f42ff4abc38c1b02f1b2742f765e91263bc9c88e6c8e3e201aa2".unhex()
    val plain = crypto.decrypt(key, data, "record:42".encodeToByteArray())
    assertEquals("Berill sync record", plain?.decodeToString())
  }

  @Test
  fun encryptsWithRandomNonceAndAuthenticatesAad() {
    val key = crypto.randomBytes(32)
    val aad = "record:1".encodeToByteArray()
    val first = crypto.encrypt(key, "secret".encodeToByteArray(), aad)
    val second = crypto.encrypt(key, "secret".encodeToByteArray(), aad)

    assertEquals(12 + 6 + 16, first.size)
    assertNotEquals(first.hex(), second.hex())
    assertContentEquals("secret".encodeToByteArray(), crypto.decrypt(key, first, aad))
    assertNull(crypto.decrypt(key, first, "record:2".encodeToByteArray()))
    assertNull(crypto.decrypt(crypto.randomBytes(32), first, aad))
    assertNull(crypto.decrypt(key, ByteArray(10), aad))
  }

  private fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

  private fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
