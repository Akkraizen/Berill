package dev.akkraizen.berill.plugins.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.layout.*
import dev.akkraizen.berill.api.ui.parseAmount
import dev.akkraizen.berill.api.ui.selection.*
import dev.akkraizen.berill.api.ui.state.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.finance.data.*
import dev.akkraizen.berill.plugins.finance.i18n.FinanceText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
internal data class QuickOperationDraft(val kind: OperationKind = OperationKind.EXPENSE, val amount: String = "", val category: String? = null, val account: String? = null, val date: String = "", val comment: String = "")

@Composable
internal fun QuickOperationDialog(ctx: PluginContext, onDismiss: () -> Unit, onError: (Exception) -> Unit) {
  val entries = LocalQuickEntries.current
  val initial = remember { QuickOperationDraft(date = today().toString()) }
  val draft = rememberQuickDraft(QuickEntryKind.OPERATION, QuickOperationDraft.serializer(), initial)
  var value by draft
  val collection = remember(ctx) { operations(ctx) }
  val recent by collection.observe().collectAsState(emptyList())
  val accs by remember(ctx) { accounts(ctx).observe() }.collectAsState(emptyList())
  val categories = rememberCategories(ctx)
  val category = value.category?.takeIf { it in categories[value.kind].orEmpty() } ?: categories.firstFor(value.kind)
  val amount = parseAmount(value.amount)
  val date = value.date.toDateOrNull()
  val valid = amount != null && amount > 0 && date != null && (!needsAccount(value.kind) || accs.any { it.value.title == value.account })
  fun from(op: Operation) = QuickOperationDraft(op.kind, formatPlain(op.amount), op.category, op.account, value.date, op.comment.orEmpty())
  QuickActionDialog(FinanceText.newOperation(), Icons.Outlined.Payments, onDismiss) {
    QuickForm {
      QuickPresets(QuickEntryKind.OPERATION, QuickOperationDraft.serializer(), value, valid,
        recent.sortedByDescending { it.value.date }.map { from(it.value) },
        { "${it.amount} · ${it.comment.ifBlank { FinanceText.categoryLabel(it.category.orEmpty()) }}" }, { value = it.copy(date = value.date) })
      ChoiceChips(KINDS, value.kind, ::kindLabel, icon = ::kindIcon, onSelect = { value = value.copy(kind = it, category = categories.firstFor(it)) })
      BerillTextField(value.amount, { value = value.copy(amount = it) }, Modifier.fillMaxWidth(), label = { Text(FinanceText.amount()) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
      DropdownField(FinanceText.category(), categories[value.kind].orEmpty(), category, { FinanceText.categoryLabel(it) }, { value = value.copy(category = it) }, Modifier.fillMaxWidth())
      if (mayHaveAccount(value.kind, category)) {
        val options = accs.map { it.value.title }
        DropdownField(FinanceText.account(), if (needsAccount(value.kind)) options else listOf(NO_ACCOUNT) + options,
          value.account ?: if (needsAccount(value.kind)) null else NO_ACCOUNT, { it }, { value = value.copy(account = it.takeUnless { it == NO_ACCOUNT }) }, Modifier.fillMaxWidth())
      }
      QuickDateField(value.date) { value = value.copy(date = it) }
      BerillTextField(value.comment, { value = value.copy(comment = it) }, Modifier.fillMaxWidth(), label = { Text(FinanceText.comment()) })
      QuickDraftActions { draft.reset(initial.copy(date = today().toString())) }
      QuickSaveButtons(valid, onDismiss) { again ->
        try {
          val op = Operation(value.kind, amount!!, category, value.account.takeIf { mayHaveAccount(value.kind, category) }, date.toString(), value.comment.trim().ifBlank { null })
          saveQuickEntry(entries, collection, ctx.data.newId(), op)
          draft.reset(QuickOperationDraft(kind = value.kind, category = category, account = value.account, date = value.date))
          if (!again) onDismiss()
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { onError(e) }
      }
    }
  }
}
