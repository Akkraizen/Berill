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

val EXPENSE = OperationKind.EXPENSE
val INCOME = OperationKind.INCOME
val DEPOSIT = OperationKind.DEPOSIT
val WITHDRAW = OperationKind.WITHDRAW
val KINDS = listOf(EXPENSE, INCOME, DEPOSIT, WITHDRAW)

fun kindLabel(kind: dev.akkraizen.berill.plugins.finance.data.OperationKind) =
  when (kind) {
    EXPENSE -> FinanceText.kindExpense()
    INCOME -> FinanceText.kindIncome()
    DEPOSIT -> FinanceText.kindDeposit()
    WITHDRAW -> FinanceText.kindWithdraw()
    else -> kind.name
  }

fun kindIcon(kind: dev.akkraizen.berill.plugins.finance.data.OperationKind): ImageVector =
  when (kind) {
    EXPENSE -> Icons.Outlined.TrendingDown
    INCOME -> Icons.Outlined.TrendingUp
    DEPOSIT -> Icons.Outlined.Savings
    WITHDRAW -> Icons.Outlined.Payments
    else -> Icons.Outlined.Receipt
  }

val INTEREST = FinanceCategory.INTEREST.name

enum class FinanceCategory {
  GROCERIES,
  CAFES,
  TRANSPORT,
  HOUSING,
  MOBILE,
  HEALTH,
  CLOTHES,
  ENTERTAINMENT,
  GIFTS,
  EDUCATION,
  OTHER,
  SALARY,
  SIDE_JOB,
  INTEREST,
  REFUND,
  SAVINGS;

  companion object {
    fun fromName(name: String): FinanceCategory? =
      entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
  }
}

val CATEGORIES: Map<dev.akkraizen.berill.plugins.finance.data.OperationKind, List<String>> = mapOf(
  EXPENSE to
    listOf(
      FinanceCategory.GROCERIES.name,
      FinanceCategory.CAFES.name,
      FinanceCategory.TRANSPORT.name,
      FinanceCategory.HOUSING.name,
      FinanceCategory.MOBILE.name,
      FinanceCategory.HEALTH.name,
      FinanceCategory.CLOTHES.name,
      FinanceCategory.ENTERTAINMENT.name,
      FinanceCategory.GIFTS.name,
      FinanceCategory.EDUCATION.name,
      FinanceCategory.OTHER.name
    ),
  INCOME to listOf(
    FinanceCategory.SALARY.name,
    FinanceCategory.SIDE_JOB.name,
    FinanceCategory.INTEREST.name,
    FinanceCategory.GIFTS.name,
    FinanceCategory.REFUND.name,
    FinanceCategory.OTHER.name
  ),
  DEPOSIT to listOf(FinanceCategory.SAVINGS.name),
  WITHDRAW to listOf(FinanceCategory.SAVINGS.name)
)

fun Map<dev.akkraizen.berill.plugins.finance.data.OperationKind, List<String>>.firstFor(kind: dev.akkraizen.berill.plugins.finance.data.OperationKind): String =
  this[kind]?.firstOrNull() ?: FinanceCategory.OTHER.name

internal fun categoriesKey(kind: dev.akkraizen.berill.plugins.finance.data.OperationKind) = "categories.${kind.legacy}"

val EDITABLE_KINDS = listOf(EXPENSE, INCOME)

fun needsAccount(kind: dev.akkraizen.berill.plugins.finance.data.OperationKind) = kind == DEPOSIT || kind == WITHDRAW

fun mayHaveAccount(kind: dev.akkraizen.berill.plugins.finance.data.OperationKind, category: String?) =
  needsAccount(kind) || (kind == INCOME && category == INTEREST)

@Serializable
data class Operation(
  val kind: dev.akkraizen.berill.plugins.finance.data.OperationKind = EXPENSE,
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
  val inMonth =
    ops.filter { it.value.date.toDateOrNull()?.let { d -> d in month } == true }.map { it.value }

  fun sum(kind: dev.akkraizen.berill.plugins.finance.data.OperationKind) = inMonth.filter { it.kind == kind }.sumOf { it.amount }

  val interestToAccounts =
    inMonth.filter { it.kind == INCOME && it.account != null }.sumOf { it.amount }
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

internal fun operations(ctx: PluginContext) =
  ctx.data.collection(FinancePlugin.OPERATIONS, Operation.serializer())

internal fun accounts(ctx: PluginContext) =
  ctx.data.collection(FinancePlugin.ACCOUNTS, Account.serializer())
