package dev.akkraizen.berill.plugins.finance.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.YearMonth
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.finance.FinancePlugin
import dev.akkraizen.berill.plugins.finance.i18n.FinanceText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val EXPENSE = "расход"
const val INCOME = "доход"
const val DEPOSIT = "пополнение"
const val WITHDRAW = "снятие"
val KINDS = listOf(EXPENSE, INCOME, DEPOSIT, WITHDRAW)

fun kindLabel(kind: String) =
  when (kind) {
    EXPENSE -> FinanceText.kindExpense()
    INCOME -> FinanceText.kindIncome()
    DEPOSIT -> FinanceText.kindDeposit()
    WITHDRAW -> FinanceText.kindWithdraw()
    else -> kind
  }

fun kindIcon(kind: String): ImageVector =
  when (kind) {
    EXPENSE -> Icons.Outlined.TrendingDown
    INCOME -> Icons.Outlined.TrendingUp
    DEPOSIT -> Icons.Outlined.Savings
    WITHDRAW -> Icons.Outlined.Payments
    else -> Icons.Outlined.Receipt
  }

const val INTEREST = "Проценты"

val CATEGORIES: Map<String, List<String>> = mapOf(
  EXPENSE to
    listOf(
      "Продукты",
      "Кафе и рестораны",
      "Транспорт",
      "Жильё и ЖКХ",
      "Связь и подписки",
      "Здоровье",
      "Одежда",
      "Развлечения",
      "Подарки",
      "Образование",
      "Другое"
    ),
  INCOME to listOf("Зарплата", "Подработка", INTEREST, "Подарки", "Возврат", "Другое"),
  DEPOSIT to listOf("Накопления"),
  WITHDRAW to listOf("Накопления")
)

fun Map<String, List<String>>.firstFor(kind: String): String = this[kind]?.firstOrNull() ?: "Другое"

internal fun categoriesKey(kind: String) = "categories.$kind"

val EDITABLE_KINDS = listOf(EXPENSE, INCOME)

fun needsAccount(kind: String) = kind == DEPOSIT || kind == WITHDRAW

fun mayHaveAccount(kind: String, category: String?) = needsAccount(kind) || (kind == INCOME && category == INTEREST)

@Serializable
data class Operation(
  val kind: String = EXPENSE,
  val amount: Double = 0.0,
  val category: String? = null,
  val account: String? = null,
  val date: String = "",
  val comment: String? = null
) {

  val signedForWallet: Double
    get() =
      when {
        kind == INCOME && account != null -> amount
        kind == INCOME || kind == WITHDRAW -> amount
        else -> -amount
      }
}

@Serializable
data class Account(
  val title: String = "",
  val bank: String? = null,
  @SerialName("initial_balance") val initialBalance: Double = 0.0,
  val goal: Double? = null,
  val rate: Double? = null,
  val opened: String? = null
)

data class MonthSummary(val income: Double, val expense: Double, val saved: Double) {
  val free: Double
    get() = income - expense - saved
}

fun summary(ops: List<Item<Operation>>, month: YearMonth): MonthSummary {
  val inMonth = ops.filter { it.value.date.toDateOrNull()?.let { d -> d in month } == true }.map { it.value }

  fun sum(kind: String) = inMonth.filter { it.kind == kind }.sumOf { it.amount }

  val interestToAccounts = inMonth.filter { it.kind == INCOME && it.account != null }.sumOf { it.amount }
  return MonthSummary(sum(INCOME), sum(EXPENSE), sum(DEPOSIT) - sum(WITHDRAW) + interestToAccounts)
}

fun balance(account: Account, ops: List<Item<Operation>>): Double =
  account.initialBalance +
    ops.map { it.value }
      .filter { it.account == account.title }
      .sumOf {
        when (it.kind) {
          DEPOSIT,
          INCOME -> it.amount

          WITHDRAW -> -it.amount
          else -> 0.0
        }
      }

fun opsOfMonth(ops: List<Item<Operation>>, month: YearMonth): List<Item<Operation>> =
  ops.filter { it.value.date.toDateOrNull()?.let { d -> d in month } == true }
    .sortedWith(compareByDescending<Item<Operation>> { it.value.date }.thenByDescending { it.id })

internal fun operations(ctx: PluginContext) = ctx.data.collection(FinancePlugin.OPERATIONS, Operation.serializer())

internal fun accounts(ctx: PluginContext) = ctx.data.collection(FinancePlugin.ACCOUNTS, Account.serializer())
