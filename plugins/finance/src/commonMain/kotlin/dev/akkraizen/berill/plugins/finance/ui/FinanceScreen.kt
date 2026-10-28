package dev.akkraizen.berill.plugins.finance.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.YearMonth
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.Tone
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.fields.MonthSwitcher
import dev.akkraizen.berill.api.ui.formatRub
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.parseAmount
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.finance.data.Account
import dev.akkraizen.berill.plugins.finance.data.EXPENSE
import dev.akkraizen.berill.plugins.finance.data.INCOME
import dev.akkraizen.berill.plugins.finance.data.KINDS
import dev.akkraizen.berill.plugins.finance.data.Operation
import dev.akkraizen.berill.plugins.finance.data.accounts
import dev.akkraizen.berill.plugins.finance.data.balance
import dev.akkraizen.berill.plugins.finance.data.firstFor
import dev.akkraizen.berill.plugins.finance.data.kindIcon
import dev.akkraizen.berill.plugins.finance.data.kindLabel
import dev.akkraizen.berill.plugins.finance.data.mayHaveAccount
import dev.akkraizen.berill.plugins.finance.data.needsAccount
import dev.akkraizen.berill.plugins.finance.data.operations
import dev.akkraizen.berill.plugins.finance.data.opsOfMonth
import dev.akkraizen.berill.plugins.finance.i18n.FinanceText

internal val NO_ACCOUNT: String
  get() = FinanceText.noAccount()

internal fun kindTone(kind: String) =
  when (kind) {
    INCOME -> Tone.Good
    EXPENSE -> Tone.Bad
    else -> Tone.Info
  }

@Composable
internal fun FinanceScreen(ctx: PluginContext) {
  val opsCollection = remember(ctx) { operations(ctx) }
  val accCollection = remember(ctx) { accounts(ctx) }
  val ops by remember(opsCollection) { opsCollection.observe() }.collectAsState(emptyList())
  val accs by remember(accCollection) { accCollection.observe() }.collectAsState(emptyList())
  var month by remember { mutableStateOf(YearMonth.current()) }
  var editingOp by remember { mutableStateOf<Item<Operation>?>(null) }
  var editingAccount by remember { mutableStateOf<Item<Account>?>(null) }
  var addingAccount by remember { mutableStateOf(false) }

  val accounts = accs.sortedBy { it.value.title.lowercase() }
  val categories = rememberCategories(ctx)
  val monthOps = opsOfMonth(ops, month)
  val byCategory = monthOps
    .map { it.value }
    .filter { it.kind == EXPENSE }
    .groupBy { it.category ?: "Другое" }
    .mapValues { (_, list) -> list.sumOf { it.amount } }
    .toList()
    .sortedByDescending { it.second }

  PluginList {
    item {
      TwoPane(
        left = {
          SectionCard(FinanceText.newOperation(), Icons.Outlined.PostAdd) {
            AddOperationForm(accounts.map { it.value.title }, categories) { op ->
              opsCollection.save(ctx.data.newId(), op)
            }
          }
        },
        right = {
          val total = accounts.sumOf { balance(it.value, ops) }
          SectionCard(
            FinanceText.savingsAccounts(formatRub(total))(),
            Icons.Outlined.AccountBalance,
            trailing = {
              TextButton(onClick = { addingAccount = true }) {
                Ic(BerillIcons.Add, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(FinanceText.account())
              }
            }
          ) {
            if (accounts.isEmpty()) EmptyHint(FinanceText.noAccountsYet())
            accounts.forEach { item ->
              AccountRow(item.value, balance(item.value, ops)) { editingAccount = item }
            }
          }
        }
      )
    }
    item { MonthSwitcher(month, { month = it }) }
    item {
      SectionCard(FinanceText.monthOperations(monthOps.size)(), Icons.Outlined.ReceiptLong) {
        if (monthOps.isEmpty()) EmptyHint(FinanceText.noOperationsThisMonth())
        monthOps.forEach { item -> OperationRow(item.value) { editingOp = item } }
      }
    }
    item { FinanceStatistics(ops.map { it.value }, month) }
    if (byCategory.isNotEmpty())
      item {
        SectionCard(FinanceText.expensesByCategory(), Icons.Outlined.PieChart) {
          val max = byCategory.first().second.coerceAtLeast(1.0)
          byCategory.forEach { (category, sum) ->
            CategoryBar(category, sum, (sum / max).toFloat())
          }
        }
      }
  }

  editingOp?.let { item ->
    EditOperationDialog(
      initial = item.value,
      categories = categories,
      accounts = accounts.map { it.value.title },
      onDismiss = { editingOp = null },
      onSave = {
        opsCollection.save(item.id, it)
        editingOp = null
      },
      onDelete = {
        opsCollection.delete(item.id)
        editingOp = null
      }
    )
  }
  editingAccount?.let { item ->
    AccountDialog(
      initial = item.value,
      onDismiss = { editingAccount = null },
      onSave = { acc ->
        if (acc.title != item.value.title) {
          ops.filter { it.value.account == item.value.title }
            .forEach { op -> opsCollection.save(op.id, op.value.copy(account = acc.title)) }
        }
        accCollection.save(item.id, acc)
        editingAccount = null
      },
      onDelete = {
        accCollection.delete(item.id)
        editingAccount = null
      }
    )
  }
  if (addingAccount)
    AccountDialog(
      initial = Account(opened = today().toString()),
      onDismiss = { addingAccount = false },
      onSave = {
        accCollection.save(ctx.data.newId(), it)
        addingAccount = false
      },
      onDelete = null
    )
}

@Composable
private fun AddOperationForm(
  accounts: List<String>,
  categories: Map<String, List<String>>,
  onSave: (Operation) -> Unit
) {
  var kind by rememberSaveable { mutableStateOf(EXPENSE) }
  var amount by rememberDraft("finance/form/amount")
  var category by rememberSaveable { mutableStateOf(categories.firstFor(EXPENSE)) }
  var account by rememberSaveable { mutableStateOf<String?>(null) }
  var date by rememberDraft("finance/form/date", today().toString())
  var comment by rememberDraft("finance/form/comment")
  var error by remember { mutableStateOf<String?>(null) }
  if (category !in categories[kind].orEmpty()) category = categories.firstFor(kind)
  fun submit() {
    val value = parseAmount(amount)
    val day = date.toDateOrNull()
    when {
      value == null || value <= 0 -> error = FinanceText.errorAmount()
      day == null -> error = FinanceText.errorDate()
      needsAccount(kind) && accounts.isEmpty() -> error = FinanceText.errorNoAccount()
      else -> {
        val acc = when {
          !mayHaveAccount(kind, category) -> null
          needsAccount(kind) -> account?.takeIf { it in accounts } ?: accounts.first()
          else -> account?.takeIf { it in accounts }
        }
        onSave(Operation(kind, value, category, acc, day.toString(), comment.trim().ifEmpty { null }))
        amount = ""
        date = today().toString()
        comment = ""
      }
    }
  }

  ChoiceChips(
    KINDS,
    kind,
    ::kindLabel,
    icon = ::kindIcon,
    onSelect = {
      kind = it
      category = categories.firstFor(it)
      error = null
    }
  )
  FieldsRow {
    BerillTextField(
      amount,
      {
        amount = it
        error = null
      },
      label = { Text(FinanceText.amount(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier = Modifier.fillMaxWidth(),
      onSubmit = { submit() }
    )
    BerillTextField(
      date,
      { date = it },
      label = { Text(FinanceText.date(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
      singleLine = true,
      isError = date.toDateOrNull() == null,
      modifier = Modifier.fillMaxWidth(),
      onSubmit = { submit() }
    )
  }
  DropdownField(
    FinanceText.category(),
    categories[kind].orEmpty(),
    category,
    { it },
    { category = it },
    Modifier.fillMaxWidth()
  )
  if (mayHaveAccount(kind, category)) {
    val options = if (needsAccount(kind)) accounts else listOf(NO_ACCOUNT) + accounts
    DropdownField(
      FinanceText.account(),
      options,
      account?.takeIf { it in options } ?: options.firstOrNull(),
      { it },
      { account = it },
      Modifier.fillMaxWidth()
    )
  }
  BerillTextField(
    comment,
    { comment = it },
    label = { Text(FinanceText.comment(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
    onSubmit = { submit() }
  )
  error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  Button(onClick = { submit() }) { Text(FinanceText.save()) }
}

internal fun formatPlain(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
