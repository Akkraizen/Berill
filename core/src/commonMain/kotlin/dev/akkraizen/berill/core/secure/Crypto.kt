package dev.akkraizen.berill.core.secure

interface CryptoPrimitives {
  fun randomBytes(size: Int): ByteArray

  fun pbkdf2(password: String, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray

  fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray

  fun decrypt(key: ByteArray, data: ByteArray, aad: ByteArray): ByteArray?
}

internal expect fun platformCrypto(): CryptoPrimitives
