package dev.akkraizen.berill.plugins.finance.data

/**
 * Values stored in synced records by older versions (and imported from Obsidian). They are data,
 * not interface text: never translate or edit them, or existing records stop matching.
 * This file is exempt from the hard-coded Cyrillic check (verifySourceLocalization).
 */
internal object LegacyValues {
  val operationKind: Map<OperationKind, String> = mapOf(
    OperationKind.EXPENSE to "расход",
    OperationKind.INCOME to "доход",
    OperationKind.DEPOSIT to "пополнение",
    OperationKind.WITHDRAW to "снятие",
  )
}
