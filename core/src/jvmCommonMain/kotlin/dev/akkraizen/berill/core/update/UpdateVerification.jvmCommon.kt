package dev.akkraizen.berill.core.update

import dev.akkraizen.berill.protocol.UpdateSignature
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal actual fun verifyEcdsaSignature(publicKey: String, message: ByteArray, signature: String): Boolean {
  val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
  return Signature.getInstance(UpdateSignature.ALGORITHM).run {
    initVerify(key)
    update(message)
    verify(Base64.getDecoder().decode(signature))
  }
}
