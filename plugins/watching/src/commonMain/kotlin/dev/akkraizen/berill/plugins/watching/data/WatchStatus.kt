package dev.akkraizen.berill.plugins.watching.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import dev.akkraizen.berill.plugins.watching.i18n.pluginI18n
import kotlinx.serialization.Serializable
@Serializable(with = WatchStatusSerializer::class)
enum class WatchStatus { PLANNED, WATCHING, WATCHED }
object WatchStatusSerializer : LegacyEnumSerializer<WatchStatus>("watching.WatchStatus", WatchStatus.entries,
 { pluginI18n.tr("legacy.WatchStatus.${it.name}").get("ru") }, WatchStatus.PLANNED)
