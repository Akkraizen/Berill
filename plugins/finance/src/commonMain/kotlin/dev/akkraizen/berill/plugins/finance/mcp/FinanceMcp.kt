package dev.akkraizen.berill.plugins.finance.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.finance.FinancePlugin
import dev.akkraizen.berill.plugins.finance.data.*

internal class FinanceMcp(ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, FinancePlugin.BUDGETS, CategoryBudget.serializer(), createId = { it.category }, validate = { require(it.category.isNotBlank() && it.amount.isFinite() && it.amount > 0) { "Budget category and positive limit required" } }),
    TypedMcpCollection(ctx, FinancePlugin.RECURRING, RecurringOperation.serializer(), fieldSchemas = mapOf("operation" to mcpSchema(Operation.serializer()).withMcpField("kind", mcpEnumSchema(OperationKind.serializer(), OperationKind.entries))), validate = { require(it.operation.amount.isFinite() && it.operation.amount > 0 && it.nextDate.toDateOrNull() != null && it.anchorDay in 1..31) { "Valid recurring operation required" } }),
    TypedMcpCollection(ctx, FinancePlugin.OPERATIONS, Operation.serializer(), fieldSchemas = mapOf("kind" to mcpEnumSchema(OperationKind.serializer(), OperationKind.entries)), normalize = { _, next -> next.copy(date = next.date.toDateOrNull()?.toString() ?: next.date) }, validate = { require(it.amount.isFinite() && it.amount > 0 && it.date.toDateOrNull() != null) { "Positive amount and valid date required" } }),
    TypedMcpCollection(ctx, FinancePlugin.ACCOUNTS, Account.serializer(), validate = { require(it.title.isNotBlank() && it.initialBalance.isFinite()) { "Account title and finite balance required" } })
  )
  override val collections: List<McpCollection> = editable + (FinancePlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}
