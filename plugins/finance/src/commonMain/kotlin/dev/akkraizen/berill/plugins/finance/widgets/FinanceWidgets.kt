package dev.akkraizen.berill.plugins.finance.widgets

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.YearMonth
import dev.akkraizen.berill.api.ui.formatRub
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetButton
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetMetric
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetSize
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.plugins.finance.FinancePlugin
import dev.akkraizen.berill.plugins.finance.data.EXPENSE
import dev.akkraizen.berill.plugins.finance.data.operations
import dev.akkraizen.berill.plugins.finance.data.opsOfMonth
import dev.akkraizen.berill.plugins.finance.data.summary
import dev.akkraizen.berill.plugins.finance.i18n.FinanceText
import dev.akkraizen.berill.plugins.finance.ui.categoriesOf

internal fun financeWidgets(ctx: PluginContext): List<WidgetProvider> = listOf(MonthWidget(ctx), QuickExpenseWidget(ctx))

internal fun expensesByCategory(ctx: PluginContext, month: YearMonth): List<Pair<String, Double>> =
  opsOfMonth(operations(ctx).all(), month)
    .map { it.value }
    .filter { it.kind == EXPENSE }
    .groupBy { it.category ?: "Другое" }
    .mapValues { (_, list) -> list.sumOf { it.amount } }
    .toList()
    .sortedByDescending { it.second }

private class MonthWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "month"
  override val title: Tr = FinanceText.widgetMonthTitle
  override val description: Tr = FinanceText.widgetMonthDescription
  override val size = WidgetSize.Medium

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val month = YearMonth.current()
    val s = summary(operations(ctx).all(), month)
    val byCategory = expensesByCategory(ctx, month)
    val max = byCategory.firstOrNull()?.second?.coerceAtLeast(1.0) ?: 1.0
    return WidgetContent(
      title = FinanceText.title(),
      subtitle = month.title,
      icon = WidgetIcon.Money,
      open = WidgetAction.Open(),
      buttons = listOf(WidgetButton(FinanceText.addExpense(), WidgetAction.Open(route = FinancePlugin.ADD_ROUTE), WidgetIcon.Add)),
      blocks = listOf(
        WidgetBlock.Metrics(
          listOf(
            WidgetMetric(FinanceText.widgetFree(), formatRub(s.free), tone = if (s.free < 0) WidgetTone.Bad else WidgetTone.Accent),
            WidgetMetric(FinanceText.income(), formatRub(s.income), tone = WidgetTone.Good),
            WidgetMetric(FinanceText.expenses(), formatRub(s.expense), tone = WidgetTone.Bad)
          )
        )
      ) + byCategory.take(CATEGORIES).map { (category, sum) ->
        WidgetBlock.Bar(category, formatRub(sum), (sum / max).toFloat(), WidgetTone.Warn)
      }
    )
  }

  override suspend fun summary(request: WidgetRequest): WidgetRow? {
    val month = YearMonth.current()
    val s = summary(operations(ctx).all(), month)
    if (s.income == 0.0 && s.expense == 0.0) return null
    return WidgetRow(
      title = FinanceText.title(),
      subtitle = "${FinanceText.expenses()}: ${formatRub(s.expense)}",
      trailing = formatRub(s.free),
      action = WidgetAction.Open(pluginId = FinancePlugin.id),
      tone = if (s.free < 0) WidgetTone.Bad else WidgetTone.Normal,
      icon = WidgetIcon.Money
    )
  }

  private companion object {
    const val CATEGORIES = 4
  }
}

private class QuickExpenseWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "quick"
  override val title: Tr = FinanceText.widgetQuickTitle
  override val description: Tr = FinanceText.widgetQuickDescription
  override val size = WidgetSize.Medium

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val day = today().toString()
    val spentToday = operations(ctx).all().map { it.value }.filter { it.kind == EXPENSE && it.date == day }.sumOf { it.amount }
    val used = expensesByCategory(ctx, YearMonth.current()).map { it.first }
    val all = categoriesOf(ctx)[EXPENSE].orEmpty()
    val ordered = (used.filter { it in all } + all).distinct().take(BUTTONS)
    return WidgetContent(
      title = title(),
      subtitle = "${FinanceText.widgetToday()}: ${formatRub(spentToday)}",
      icon = WidgetIcon.Money,
      open = WidgetAction.Open(),
      blocks = listOf(
        WidgetBlock.Buttons(
          ordered.map { WidgetButton(it, WidgetAction.Open(route = FinancePlugin.ADD_ROUTE + it), WidgetIcon.Add) } +
            WidgetButton(FinanceText.widgetOther(), WidgetAction.Open(route = FinancePlugin.ADD_ROUTE), WidgetIcon.Money, primary = true)
        )
      )
    )
  }

  private companion object {
    const val BUTTONS = 6
  }
}
