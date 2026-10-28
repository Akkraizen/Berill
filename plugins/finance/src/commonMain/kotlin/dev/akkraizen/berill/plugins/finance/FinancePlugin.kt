package dev.akkraizen.berill.plugins.finance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.plugins.finance.i18n.FinanceText
import dev.akkraizen.berill.plugins.finance.ui.FinanceHomeCard
import dev.akkraizen.berill.plugins.finance.ui.FinanceScreen
import dev.akkraizen.berill.plugins.finance.ui.FinanceSettings
import dev.akkraizen.berill.plugins.finance.widgets.financeWidgets

object FinancePlugin : BerillPlugin {
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

  override val collections = listOf(OPERATIONS, ACCOUNTS)

  @Composable
  override fun Screen(ctx: PluginContext) = FinanceScreen(ctx)

  override fun homeCards(ctx: PluginContext) =
    listOf(HomeCard("month", FinanceText.homeCardTitle, order = 40) { FinanceHomeCard(ctx) })

  override fun widgets(ctx: PluginContext) = financeWidgets(ctx)
}
