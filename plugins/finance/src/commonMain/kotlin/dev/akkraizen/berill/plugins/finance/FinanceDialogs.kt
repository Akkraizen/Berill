package dev.akkraizen.berill.plugins.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.parseAmount
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.api.ui.toDateOrNull

@Composable
internal fun EditOperationDialog(
  initial: Operation,
  categories: Map<String, List<String>>,
  accounts: List<String>,
  onDismiss: () -> Unit,
  onSave: (Operation) -> Unit,
  onDelete: () -> Unit
) {
  var kind by remember { mutableStateOf(initial.kind) }
  var amount by remember { mutableStateOf(formatPlain(initial.amount)) }
  var category by remember { mutableStateOf(initial.category ?: categories.firstFor(initial.kind)) }
  var account by remember { mutableStateOf(initial.account) }
  var date by remember { mutableStateOf(initial.date) }
  var comment by remember { mutableStateOf(initial.comment ?: "") }
  var askDelete by remember { mutableStateOf(false) }
  val value = parseAmount(amount)
  val valid = value != null &&
    value > 0 &&
    date.toDateOrNull() != null &&
    (!needsAccount(kind) || (account != null && account in accounts))
  val confirm: () -> Unit = {
    onSave(
      Operation(
        kind,
        value ?: 0.0,
        category,
        if (mayHaveAccount(kind, category)) account else null,
        date.toDateOrNull().toString(),
        comment.trim().ifEmpty { null }
      )
    )
  }

  DialogKeys(onConfirm = confirm, confirmEnabled = valid, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(FinanceText.operation()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChips(
          KINDS,
          kind,
          ::kindLabel,
          icon = ::kindIcon,
          onSelect = {
            kind = it
            if (category !in categories[it].orEmpty()) category = categories.firstFor(it)
          }
        )
        BerillTextField(
          amount,
          { amount = it },
          label = { Text(FinanceText.amount()) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        DropdownField(
          FinanceText.category(),
          categories[kind].orEmpty(),
          category,
          { it },
          { category = it }
        )
        if (mayHaveAccount(kind, category)) {
          val options = if (needsAccount(kind)) accounts else listOf(NO_ACCOUNT) + accounts
          DropdownField(
            FinanceText.account(),
            options,
            account?.takeIf { it in options } ?: if (needsAccount(kind)) null else NO_ACCOUNT,
            { it },
            { account = it.takeIf { a -> a != NO_ACCOUNT } }
          )
        }
        BerillTextField(
          date,
          { date = it },
          label = { Text(FinanceText.date()) },
          singleLine = true,
          isError = date.toDateOrNull() == null
        )
        BerillTextField(comment, { comment = it }, label = { Text(FinanceText.comment()) }, singleLine = true)
        TextButton(onClick = { askDelete = true }) {
          Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.error)
          Spacer(Modifier.width(6.dp))
          Text(FinanceText.deleteOperation(), color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = valid, onClick = confirm) { Text(FinanceText.save()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(FinanceText.cancel()) } }
  )
  if (askDelete)
    ConfirmDialog(
      FinanceText.deleteOperationQuestion(),
      FinanceText.deleteOperationText(),
      onConfirm = onDelete,
      onDismiss = { askDelete = false }
    )
}

@Composable
internal fun AccountDialog(initial: Account, onDismiss: () -> Unit, onSave: (Account) -> Unit, onDelete: (() -> Unit)?) {
  var title by remember { mutableStateOf(initial.title) }
  var bank by remember { mutableStateOf(initial.bank ?: "") }
  var start by remember { mutableStateOf(formatPlain(initial.initialBalance)) }
  var goal by remember { mutableStateOf(initial.goal?.let(::formatPlain) ?: "") }
  var rate by remember { mutableStateOf(initial.rate?.let(::formatPlain) ?: "") }
  var askDelete by remember { mutableStateOf(false) }
  val confirm: () -> Unit = {
    onSave(
      initial.copy(
        title = title.trim(),
        bank = bank.trim().ifEmpty { null },
        initialBalance = parseAmount(start) ?: 0.0,
        goal = parseAmount(goal),
        rate = parseAmount(rate)
      )
    )
  }

  DialogKeys(onConfirm = confirm, confirmEnabled = title.isNotBlank(), onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (onDelete == null) FinanceText.newAccount() else FinanceText.account()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(title, { title = it }, label = { Text(FinanceText.name()) }, singleLine = true)
        BerillTextField(bank, { bank = it }, label = { Text(FinanceText.bank()) }, singleLine = true)
        BerillTextField(
          start,
          { start = it },
          label = { Text(FinanceText.initialBalance()) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        BerillTextField(
          goal,
          { goal = it },
          label = { Text(FinanceText.goal()) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        BerillTextField(
          rate,
          { rate = it },
          label = { Text(FinanceText.rate()) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        if (onDelete != null)
          TextButton(onClick = { askDelete = true }) {
            Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(FinanceText.deleteAccount(), color = MaterialTheme.colorScheme.error)
          }
      }
    },
    confirmButton = {
      TextButton(enabled = title.isNotBlank(), onClick = confirm) { Text(FinanceText.save()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(FinanceText.cancel()) } }
  )
  if (askDelete && onDelete != null)
    ConfirmDialog(
      FinanceText.deleteAccountQuestion(),
      FinanceText.deleteAccountText(),
      onConfirm = onDelete,
      onDismiss = { askDelete = false }
    )
}
