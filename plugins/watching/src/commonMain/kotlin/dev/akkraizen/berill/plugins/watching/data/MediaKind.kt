package dev.akkraizen.berill.plugins.watching.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import kotlinx.serialization.Serializable
@Serializable(with = MediaKindSerializer::class)
enum class MediaKind { ANIME, MOVIE, SERIES }
object MediaKindSerializer : LegacyEnumSerializer<MediaKind>("watching.MediaKind", MediaKind.entries,
 { LegacyValues.mediaKind.getValue(it) }, MediaKind.ANIME)
