package dev.akkraizen.berill.plugins.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.formatRub
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.finance.data.*
import dev.akkraizen.berill.plugins.finance.i18n.pluginI18n
import kotlinx.datetime.LocalDate

internal fun automationText(key: String, language: String = I18n.language.code) =
  pluginI18n.tr("finance.automation.$key").get(language)

@Composable
internal fun FinanceAutomation(ctx: PluginContext, ops: List<Item<Operation>>) {
  val categories = rememberCategories(ctx)
  val rules = remember(ctx) { recurring(ctx) }
  val limits = remember(ctx) { budgets(ctx) }
  val allRules by remember(rules) { rules.observe() }.collectAsState(emptyList())
  val allLimits by remember(limits) { limits.observe() }.collectAsState(emptyList())
  var adding by remember { mutableStateOf(false) }
  var monthly by remember { mutableStateOf(true) }
  var budgetDialog by remember { mutableStateOf(false) }
  var category by remember { mutableStateOf("") }
  var amount by remember { mutableStateOf("") }
  LaunchedEffect(Unit) { materializeRecurring(ctx) }
  SectionCard(automationText("title"), Icons.Outlined.AccountBalanceWallet) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(
        selected = monthly,
        onClick = { monthly = true },
        label = { Text(automationText("monthly")) },
      )
      FilterChip(
        selected = !monthly,
        onClick = { monthly = false },
        label = { Text(automationText("daily")) },
      )
    }
    FilledTonalButton(onClick = { adding = true }) {
      Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
      Text(automationText("add"))
    }
    allRules.forEach { item ->
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        Column(
          Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            item.value.operation.comment ?: item.value.operation.category.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            "${formatRub(item.value.operation.amount)} · ${item.value.nextDate}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
              onClick = { rules.save(item.id, item.value.copy(enabled = !item.value.enabled)) }
            ) {
              Icon(
                if (item.value.enabled) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                null,
                Modifier.size(20.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(automationText(if (item.value.enabled) "pause" else "resume"))
            }
            TextButton(
              onClick = { rules.delete(item.id) },
              colors =
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
              Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(20.dp))
              Spacer(Modifier.width(6.dp))
              Text(automationText("delete"))
            }
          }
        }
      }
    }
    OutlinedButton(onClick = { budgetDialog = true }) {
      Icon(Icons.Outlined.Savings, null, Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
      Text(automationText("budget"))
    }
    val month = today().toString().take(7)
    allLimits.forEach { item ->
      val spent =
        ops
          .filter {
            it.value.kind == EXPENSE &&
              it.value.category == item.value.category &&
              it.value.date.startsWith(month)
          }
          .sumOf { it.value.amount }
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        Column(
          Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                dev.akkraizen.berill.plugins.finance.i18n.FinanceText.categoryLabel(
                  item.value.category
                ),
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                "${formatRub(spent)} / ${formatRub(item.value.amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(
              onClick = { limits.delete(item.id) },
              colors =
                IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
              Icon(Icons.Outlined.DeleteOutline, automationText("delete"))
            }
          }
          LinearProgressIndicator(
            progress = { (spent / item.value.amount).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color =
              if (spent > item.value.amount) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.primary,
            drawStopIndicator = {},
          )
        }
      }
    }
  }
  if (adding) {
    EditOperationDialog(
      initial = Operation(date = today().toString()),
      categories = rememberCategories(ctx),
      accounts = accounts(ctx).all().map { it.value.title },
      onDismiss = { adding = false },
      onSave = { op ->
        val date = runCatching { LocalDate.parse(op.date) }.getOrNull() ?: today()
        rules.save(ctx.data.newId(), RecurringOperation(op, date.toString(), monthly, date.day))
        materializeRecurring(ctx)
        adding = false
      },
      onDelete = null,
    )
  }
  if (budgetDialog)
    AlertDialog(
      onDismissRequest = { budgetDialog = false },
      icon = { Icon(Icons.Outlined.Savings, null) },
      title = { Text(automationText("budget")) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          dev.akkraizen.berill.api.ui.selection.DropdownField(
            automationText("category"),
            categories[EXPENSE].orEmpty(),
            category.takeIf { it.isNotBlank() },
            { dev.akkraizen.berill.plugins.finance.i18n.FinanceText.categoryLabel(it) },
            { category = it },
          )
          OutlinedTextField(
            amount,
            { amount = it },
            label = { Text(automationText("limit")) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
          )
        }
      },
      confirmButton = {
        FilledTonalButton(
          enabled =
            category.isNotBlank() &&
              (amount.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0) > 0.0,
          onClick = {
            limits.save(
              category.trim(),
              CategoryBudget(category.trim(), amount.replace(',', '.').toDouble()),
            )
            budgetDialog = false
          },
        ) {
          Text(automationText("save"))
        }
      },
      dismissButton = {
        TextButton(onClick = { budgetDialog = false }) {
          Text(dev.akkraizen.berill.plugins.finance.i18n.FinanceText.cancel())
        }
      },
    )
}
