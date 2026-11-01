package dev.akkraizen.berill.plugins.finance.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import dev.akkraizen.berill.plugins.finance.i18n.pluginI18n
import kotlinx.serialization.Serializable
@Serializable(with = OperationKindSerializer::class)
enum class OperationKind { EXPENSE, INCOME, DEPOSIT, WITHDRAW;
 val legacy: String get() = pluginI18n.tr("legacy.OperationKind.$name").get("ru")
}
object OperationKindSerializer : LegacyEnumSerializer<OperationKind>("finance.kind", OperationKind.entries,
 { it.legacy }, OperationKind.EXPENSE)
