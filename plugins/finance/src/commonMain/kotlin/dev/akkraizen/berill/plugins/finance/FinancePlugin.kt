package dev.akkraizen.berill.plugins.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.YearMonth
import dev.akkraizen.berill.api.ui.cards.Stat
import dev.akkraizen.berill.api.ui.cards.StatTiles
import dev.akkraizen.berill.api.ui.feedback.Tone
import dev.akkraizen.berill.api.ui.formatRub
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.parseAmount
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.api.ui.today

object FinancePlugin : BerillPlugin {
  const val OPERATIONS = "operations"
  const val ACCOUNTS = "accounts"

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
}

internal fun operations(ctx: PluginContext) = ctx.data.collection(FinancePlugin.OPERATIONS, Operation.serializer())

internal fun accounts(ctx: PluginContext) = ctx.data.collection(FinancePlugin.ACCOUNTS, Account.serializer())

@Composable
private fun FinanceHomeCard(ctx: PluginContext) {
  val opsCollection = remember(ctx) { operations(ctx) }
  val ops by remember(opsCollection) { opsCollection.observe() }.collectAsState(emptyList())
  val accs by remember(ctx) { accounts(ctx).observe() }.collectAsState(emptyList())
  val s = summary(ops, YearMonth.current())
  val savings = accs.sumOf { balance(it.value, ops) }
  StatTiles(
    listOf(
      Stat(FinanceText.income(), formatRub(s.income), tone = Tone.Good, icon = kindIcon(INCOME)),
      Stat(FinanceText.expenses(), formatRub(s.expense), tone = Tone.Bad, icon = kindIcon(EXPENSE)),
      Stat(FinanceText.onAccounts(), formatRub(savings), tone = Tone.Info, icon = Icons.Outlined.AccountBalance)
    ),
    minTileWidth = 100.dp
  )
  var amount by rememberDraft("finance/quick/amount")
  val categories = rememberCategories(ctx)
  var category by remember { mutableStateOf(categories.firstFor(EXPENSE)) }

  if (category !in categories[EXPENSE].orEmpty()) category = categories.firstFor(EXPENSE)
  fun addExpense() {
    val value = parseAmount(amount)?.takeIf { it > 0 } ?: return
    opsCollection.save(ctx.data.newId(), Operation(EXPENSE, value, category, null, today().toString()))
    amount = ""
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    BerillTextField(
      amount,
      { amount = it },
      placeholder = { Text(FinanceText.quickExpense()) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier = Modifier.weight(1f),
      onSubmit = { addExpense() }
    )
    FilledTonalButton(enabled = (parseAmount(amount) ?: 0.0) > 0, onClick = { addExpense() }) {
      Icon(BerillIcons.Add, contentDescription = FinanceText.addExpense())
    }
  }
  DropdownField(
    FinanceText.category(),
    categories[EXPENSE].orEmpty(),
    category,
    { it },
    { category = it }
  )
}
