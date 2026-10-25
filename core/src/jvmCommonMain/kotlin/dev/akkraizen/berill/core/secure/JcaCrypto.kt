package dev.akkraizen.berill.core.secure

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private object JcaCrypto : CryptoPrimitives {
  private val random = SecureRandom()

  override fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

  override fun pbkdf2(password: String, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyBytes * 8)
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  override fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
    val nonce = randomBytes(12)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    cipher.updateAAD(aad)
    return nonce + cipher.doFinal(plaintext)
  }

  override fun decrypt(key: ByteArray, data: ByteArray, aad: ByteArray): ByteArray? =
    runCatching {
      if (data.size < 12 + 16) return null
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, data, 0, 12))
      cipher.updateAAD(aad)
      cipher.doFinal(data, 12, data.size - 12)
    }
      .getOrNull()
}

internal actual fun platformCrypto(): CryptoPrimitives = JcaCrypto
