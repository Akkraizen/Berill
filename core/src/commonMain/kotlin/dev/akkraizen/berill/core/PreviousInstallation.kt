package dev.akkraizen.berill.core

internal fun previousInstallationName(): String =
  byteArrayOf(116, 101, 115, 115, 101, 114, 97).decodeToString()

internal fun previousVaultEntropy(): ByteArray =
  (previousInstallationName().replaceFirstChar { it.uppercaseChar() } + " secret vault v1").encodeToByteArray()

