package dev.akkraizen.berill.plugins.watching.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import dev.akkraizen.berill.plugins.watching.i18n.pluginI18n
import kotlinx.serialization.Serializable
@Serializable(with = MediaKindSerializer::class)
enum class MediaKind { ANIME, MOVIE, SERIES }
object MediaKindSerializer : LegacyEnumSerializer<MediaKind>("watching.MediaKind", MediaKind.entries,
 { pluginI18n.tr("legacy.MediaKind.${it.name}").get("ru") }, MediaKind.ANIME)
