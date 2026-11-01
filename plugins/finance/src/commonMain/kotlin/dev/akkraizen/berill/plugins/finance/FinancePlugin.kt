package dev.akkraizen.berill.plugins.finance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.label
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.plugins.finance.i18n.FinanceText
import dev.akkraizen.berill.plugins.finance.ui.FinanceHomeCard
import dev.akkraizen.berill.plugins.finance.ui.FinanceScreen
import dev.akkraizen.berill.plugins.finance.ui.FinanceSettings
import dev.akkraizen.berill.plugins.finance.widgets.financeWidgets

object FinancePlugin : BerillPlugin {
  override fun provides(ctx: dev.akkraizen.berill.api.PluginContext): List<dev.akkraizen.berill.api.Capability> = baseProvides(ctx) + dev.akkraizen.berill.plugins.finance.mcp.FinanceMcp(ctx)

  const val RECURRING = "recurring_operations"
  const val BUDGETS = "category_budgets"
  const val OPERATIONS = "operations"
  const val ACCOUNTS = "accounts"

  internal const val ADD_ROUTE = "add:"

  override val id = "finance"
  override val title
    get() = FinanceText.title()

  override val icon = Icons.Outlined.AccountBalanceWallet
  override val order = 40
  override val description
    get() = FinanceText.description()

  override val installedByDefault = true
  override val hasSettings = true

  @Composable
  override fun Settings(ctx: PluginContext) = FinanceSettings(ctx)

  override val collections = listOf(RECURRING, BUDGETS, OPERATIONS, ACCOUNTS)

  @Composable
  override fun Screen(ctx: PluginContext) = FinanceScreen(ctx)

  override fun quickActions(ctx: PluginContext) = listOf(
    dev.akkraizen.berill.api.QuickAction("new-operation", FinanceText.newOperation, icon) {
      ctx.nav.openPlugin(id)
      ctx.nav.open(ADD_ROUTE)
    }.withPopup { close, error -> dev.akkraizen.berill.plugins.finance.ui.QuickOperationDialog(ctx, close, error) }
  )

  override fun homeCards(ctx: PluginContext) =
    listOf(HomeCard("month", FinanceText.homeCardTitle, order = 40) { FinanceHomeCard(ctx) })

  private fun baseProvides(ctx: PluginContext): List<dev.akkraizen.berill.api.Capability> = listOf(dev.akkraizen.berill.plugins.finance.data.RecurringMaintenance(ctx), dev.akkraizen.berill.api.CollectionDaySource(ctx, OPERATIONS, { title }, describe = { "${it.label()}: ${it.fields["amount"] ?: ""} ${it.fields["currency"] ?: ""}" }), dev.akkraizen.berill.api.CollectionSearchSource(ctx, OPERATIONS, { title }, { "operation:$it" }))

  override fun widgets(ctx: PluginContext) = financeWidgets(ctx)
}
