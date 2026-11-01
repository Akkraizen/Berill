package dev.akkraizen.berill.plugins.watching.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import kotlinx.serialization.Serializable
@Serializable(with = WatchStatusSerializer::class)
enum class WatchStatus { PLANNED, WATCHING, WATCHED }
object WatchStatusSerializer : LegacyEnumSerializer<WatchStatus>("watching.WatchStatus", WatchStatus.entries,
 { LegacyValues.watchStatus.getValue(it) }, WatchStatus.PLANNED)
