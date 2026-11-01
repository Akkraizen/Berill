package dev.akkraizen.berill.core.connections

import dev.akkraizen.berill.protocol.SyncRecord

expect fun encodeLocalArchive(records: List<SyncRecord>, attachments: Map<String, ByteArray> = emptyMap()): ByteArray
expect fun decodeLocalArchive(bytes: ByteArray): List<SyncRecord>

expect fun decodeArchiveAttachments(bytes: ByteArray): Map<String, ByteArray>
