package dev.akkraizen.berill.protocol

/**
 * What the release workflow signs for every installation package. The update server only relays
 * the signature: a client that has the publisher's public key accepts a package only if the file
 * name, SHA-256, and size it is about to install were signed by the private key kept in CI.
 */
object UpdateSignature {
  const val ALGORITHM = "SHA256withECDSA"

  fun message(fileName: String, sha256: String, size: Long): String = "berill-update-v1\n$fileName\n$sha256\n$size\n"

  /** Name of the file next to the packages that maps package file names to signatures. */
  fun signaturesFile(version: String): String = "Berill-$version-signatures.json"
}
