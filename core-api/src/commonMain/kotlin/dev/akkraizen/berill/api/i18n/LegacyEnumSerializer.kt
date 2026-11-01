package dev.akkraizen.berill.api.i18n

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Typed application keys with unchanged wire values for older clients.
 * The legacy.* catalog entries are immutable data aliases, never UI labels.
 * They are read in the historical language regardless of the selected UI language.
 */
open class LegacyEnumSerializer<E : Enum<E>>(
  name: String, private val values: List<E>, private val legacy: (E) -> String, private val fallback: E
) : KSerializer<E> {
  override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
  override fun deserialize(decoder: Decoder): E {
    val value = decoder.decodeString()
    return values.firstOrNull { it.name == value || legacy(it) == value } ?: fallback
  }
  override fun serialize(encoder: Encoder, value: E) { encoder.encodeString(legacy(value)) }
}
