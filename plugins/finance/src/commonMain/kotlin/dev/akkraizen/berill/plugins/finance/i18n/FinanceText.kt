package dev.akkraizen.berill.plugins.finance.i18n

internal object FinanceText {

  val title = pluginI18n.tr("finance.title")
  val description = pluginI18n.tr("finance.description")
  val homeCardTitle = pluginI18n.tr("finance.homeCardTitle")
  val income = pluginI18n.tr("finance.income")
  val expenses = pluginI18n.tr("finance.expenses")
  val onAccounts = pluginI18n.tr("finance.onAccounts")
  val quickExpense = pluginI18n.tr("finance.quickExpense")
  val addExpense = pluginI18n.tr("finance.addExpense")
  val category = pluginI18n.tr("finance.category")

  val kindExpense = pluginI18n.tr("finance.kindExpense")
  val kindIncome = pluginI18n.tr("finance.kindIncome")
  val kindDeposit = pluginI18n.tr("finance.kindDeposit")
  val kindWithdraw = pluginI18n.tr("finance.kindWithdraw")

  val noAccount = pluginI18n.tr("finance.noAccount")
  val newOperation = pluginI18n.tr("finance.newOperation")

  fun savingsAccounts(total: String) = pluginI18n.tr("finance.savingsAccounts", "total" to total)

  val account = pluginI18n.tr("finance.account")
  val noAccountsYet = pluginI18n.tr("finance.noAccountsYet")
  val savedToAccounts = pluginI18n.tr("finance.savedToAccounts")
  val leftFree = pluginI18n.tr("finance.leftFree")
  val expensesByCategory = pluginI18n.tr("finance.expensesByCategory")

  fun monthOperations(n: Int) = pluginI18n.tr("finance.monthOperations", "n" to n)

  val noOperationsThisMonth = pluginI18n.tr("finance.noOperationsThisMonth")

  val amount = pluginI18n.tr("finance.amount")
  val date = pluginI18n.tr("finance.date")
  val comment = pluginI18n.tr("finance.comment")
  val errorAmount = pluginI18n.tr("finance.errorAmount")
  val errorDate = pluginI18n.tr("finance.errorDate")
  val errorNoAccount = pluginI18n.tr("finance.errorNoAccount")
  val save = pluginI18n.tr("finance.save")
  val cancel = pluginI18n.tr("finance.cancel")

  fun ofGoal(pct: Int, goal: String) = pluginI18n.tr("finance.ofGoal", "pct" to pct, "goal" to goal)

  val operation = pluginI18n.tr("finance.operation")
  val deleteOperation = pluginI18n.tr("finance.deleteOperation")
  val deleteOperationQuestion = pluginI18n.tr("finance.deleteOperationQuestion")
  val deleteOperationText =
    pluginI18n.tr("finance.deleteOperationText")

  val newAccount = pluginI18n.tr("finance.newAccount")
  val name = pluginI18n.tr("finance.name")
  val bank = pluginI18n.tr("finance.bank")
  val initialBalance = pluginI18n.tr("finance.initialBalance")
  val goal = pluginI18n.tr("finance.goal")
  val rate = pluginI18n.tr("finance.rate")
  val deleteAccount = pluginI18n.tr("finance.deleteAccount")
  val deleteAccountQuestion = pluginI18n.tr("finance.deleteAccountQuestion")
  val deleteAccountText = pluginI18n.tr("finance.deleteAccountText")

  val expenseCategories = pluginI18n.tr("finance.expenseCategories")
  val incomeCategories = pluginI18n.tr("finance.incomeCategories")
  val newCategory = pluginI18n.tr("finance.newCategory")

  fun interestHint(interest: String) =
    pluginI18n.tr("finance.interestHint", "interest" to interest)

  val deletedCategoryHint = pluginI18n.tr("finance.deletedCategoryHint")

  val widgetMonthTitle = pluginI18n.tr("finance.widget.monthTitle")
  val widgetMonthDescription = pluginI18n.tr("finance.widget.monthDescription")
  val widgetQuickTitle = pluginI18n.tr("finance.widget.quickTitle")
  val widgetQuickDescription = pluginI18n.tr("finance.widget.quickDescription")
  val widgetFree = pluginI18n.tr("finance.widget.free")
  val widgetToday = pluginI18n.tr("finance.widget.today")
  val widgetOther = pluginI18n.tr("finance.widget.other")
}
