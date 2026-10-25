package dev.akkraizen.berill.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object FlexibleStringList : KSerializer<List<String>> {
  private val delegate = ListSerializer(String.serializer())
  override val descriptor: SerialDescriptor = delegate.descriptor

  override fun serialize(encoder: Encoder, value: List<String>) = delegate.serialize(encoder, value)

  override fun deserialize(decoder: Decoder): List<String> {
    if (decoder !is JsonDecoder) return delegate.deserialize(decoder)
    return when (val element = decoder.decodeJsonElement()) {
      is JsonArray ->
        element.mapNotNull {
          (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        }

      is JsonNull -> emptyList()
      is JsonPrimitive ->
        element.jsonPrimitive.content.split(',').map(String::trim).filter(String::isNotEmpty)

      else -> emptyList()
    }
  }
}
