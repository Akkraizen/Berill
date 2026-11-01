package dev.akkraizen.berill.plugins.finance.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import kotlinx.serialization.Serializable
@Serializable(with = OperationKindSerializer::class)
enum class OperationKind { EXPENSE, INCOME, DEPOSIT, WITHDRAW;
 val legacy: String get() = LegacyValues.operationKind.getValue(this)
}
object OperationKindSerializer : LegacyEnumSerializer<OperationKind>("finance.kind", OperationKind.entries,
 { it.legacy }, OperationKind.EXPENSE)
