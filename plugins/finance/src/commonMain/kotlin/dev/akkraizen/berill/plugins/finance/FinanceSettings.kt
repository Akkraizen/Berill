package dev.akkraizen.berill.plugins.finance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.observeStringList
import dev.akkraizen.berill.api.setStringList
import dev.akkraizen.berill.api.stringList
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.fields.EditableList

@Composable
internal fun rememberCategories(ctx: PluginContext): Map<String, List<String>> {
  val expense by
    remember(ctx) { ctx.settings.observeStringList(categoriesKey(EXPENSE)) }
      .collectAsState(ctx.settings.stringList(categoriesKey(EXPENSE)))
  val income by
    remember(ctx) { ctx.settings.observeStringList(categoriesKey(INCOME)) }
      .collectAsState(ctx.settings.stringList(categoriesKey(INCOME)))
  return CATEGORIES +
    mapOf(
      EXPENSE to (expense?.takeIf { it.isNotEmpty() } ?: CATEGORIES.getValue(EXPENSE)),
      INCOME to (income?.takeIf { it.isNotEmpty() } ?: CATEGORIES.getValue(INCOME))
    )
}

@Composable
internal fun FinanceSettings(ctx: PluginContext) {
  val categories = rememberCategories(ctx)
  EDITABLE_KINDS.forEach { kind ->
    SectionCard(
      if (kind == EXPENSE) FinanceText.expenseCategories() else FinanceText.incomeCategories(),
      Icons.Outlined.Category
    ) {
      EditableList(
        items = categories[kind].orEmpty(),
        onChange = { ctx.settings.setStringList(categoriesKey(kind), it) },
        placeholder = FinanceText.newCategory(),
        onReset = {
          ctx.settings.setStringList(categoriesKey(kind), CATEGORIES.getValue(kind))
        },
        draftKey = "finance/categories/$kind"
      )
      if (kind == INCOME)
        Text(
          FinanceText.interestHint(INTEREST)(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
  }
  Text(
    FinanceText.deletedCategoryHint(),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}
