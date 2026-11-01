package dev.akkraizen.berill.core.update

import dev.akkraizen.berill.protocol.UpdateSignature

/** Verifies an ECDSA P-256 signature; [publicKey] is a Base64 X.509 SubjectPublicKeyInfo. */
internal expect fun verifyEcdsaSignature(publicKey: String, message: ByteArray, signature: String): Boolean

/**
 * Publisher check for installation packages. Without a configured key (development builds and
 * forks), packages are accepted on SHA-256 alone, as before; with a key, an unsigned or wrongly
 * signed package is refused before it is downloaded and again before it is installed.
 */
internal fun requirePublisherSignature(publicKey: String, update: AppUpdate, fileName: String) {
  if (publicKey.isBlank()) return
  val sha256 = update.sha256 ?: throw UpdateException(UpdateFailure.SERVER_TOO_OLD)
  val size = update.size ?: throw UpdateException(UpdateFailure.SERVER_TOO_OLD)
  val signature = update.signature ?: throw UpdateException(UpdateFailure.SIGNATURE)
  val message = UpdateSignature.message(fileName, sha256, size).encodeToByteArray()
  val valid = try {
    verifyEcdsaSignature(publicKey.trim(), message, signature)
  } catch (e: Exception) {
    false
  }
  if (!valid) throw UpdateException(UpdateFailure.SIGNATURE)
}
