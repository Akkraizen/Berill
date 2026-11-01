package dev.akkraizen.berill.plugins.finance.data

import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.finance.FinancePlugin
import kotlinx.datetime.*
import kotlinx.serialization.Serializable

@Serializable
data class RecurringOperation(val operation: Operation, val nextDate: String, val monthly: Boolean = true, val anchorDay: Int = 1, val enabled: Boolean = true)
@Serializable
data class CategoryBudget(val category: String, val amount: Double)

internal fun recurring(ctx: PluginContext) = ctx.data.collection(FinancePlugin.RECURRING, RecurringOperation.serializer())
internal fun budgets(ctx: PluginContext) = ctx.data.collection(FinancePlugin.BUDGETS, CategoryBudget.serializer())

fun nextOccurrence(date: LocalDate, monthly: Boolean, anchorDay: Int): LocalDate {
  if (!monthly) return date.plus(1, DateTimeUnit.DAY)
  val next = LocalDate(date.year, date.month, 1).plus(1, DateTimeUnit.MONTH)
  val end = next.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
  return LocalDate(next.year, next.month, anchorDay.coerceIn(1, end.day))
}

internal fun materializeRecurring(ctx: PluginContext, until: LocalDate = today()) {
  val rules = recurring(ctx)
  val operations = ctx.data.collection(FinancePlugin.OPERATIONS, Operation.serializer())
  for (item in rules.all().filter { it.value.enabled }) {
    var next = runCatching { LocalDate.parse(item.value.nextDate) }.getOrNull() ?: continue
    var count = 0
    while (next <= until && count++ < 366) {
      val id = "recurring/${item.id}/$next"
      if (operations.get(id) == null && !ctx.data.raw(FinancePlugin.OPERATIONS).isDeleted(id)) operations.save(id, item.value.operation.copy(date = next.toString()))
      next = nextOccurrence(next, item.value.monthly, item.value.anchorDay)
      rules.save(item.id, item.value.copy(nextDate = next.toString()))
    }
  }
}

internal class RecurringMaintenance(private val ctx: PluginContext) : MaintenanceSource {
  override suspend fun update() { materializeRecurring(ctx) }
}
