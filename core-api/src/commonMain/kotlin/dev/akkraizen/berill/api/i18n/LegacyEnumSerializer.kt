package dev.akkraizen.berill.api.i18n

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Typed application keys with unchanged wire values for older clients. [legacy] returns the stored
 * value (kept in each plugin's `data/LegacyValues.kt`, never in translation catalogs). Unknown values,
 * for example from a newer version, decode as [fallback]; typed saves keep the stored raw value until
 * the user actually changes that field, so such values are not overwritten.
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
