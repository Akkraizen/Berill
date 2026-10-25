package dev.akkraizen.berill.plugins.habits

internal object HabitsText {

  val title = pluginI18n.tr("habits.title")
  val description = pluginI18n.tr("habits.description")
  val todayCardTitle = pluginI18n.tr("habits.todayCardTitle")

  val unitMinutes = pluginI18n.tr("habits.unitMinutes")
  val unitProblems = pluginI18n.tr("habits.unitProblems")
  val unitTimes = pluginI18n.tr("habits.unitTimes")

  fun problems(n: Int) =
    pluginI18n.plural("habits.problems", n)

  fun times(n: Int) =
    pluginI18n.plural("habits.times", n)

  val previousDay = pluginI18n.tr("habits.previousDay")
  val nextDay = pluginI18n.tr("habits.nextDay")
  val today = pluginI18n.tr("habits.today")

  fun doneOf(done: Int, total: Int) = pluginI18n.tr("habits.doneOf", "done" to done, "total" to total)

  fun shortDoneOf(done: Int, total: Int) = pluginI18n.tr("habits.shortDoneOf", "done" to done, "total" to total)

  val statistics = pluginI18n.tr("habits.statistics")
  val habits = pluginI18n.tr("habits.habits")
  val noHabitsAddFirst = pluginI18n.tr("habits.noHabitsAddFirst")
  val noHabits = pluginI18n.tr("habits.noHabits")
  val habit = pluginI18n.tr("habits.habit")

  fun cardDone(amount: String) = pluginI18n.tr("habits.cardDone", "amount" to amount)

  fun cardProgress(value: String, norm: String) = pluginI18n.tr("habits.cardProgress", "value" to value, "norm" to norm)

  fun cardNorm(norm: String) = pluginI18n.tr("habits.cardNorm", "norm" to norm)

  val last7Days = pluginI18n.tr("habits.last7Days")

  fun valueLabel(unit: String, norm: Int) = pluginI18n.tr("habits.valueLabel", "unit" to unit, "norm" to norm)

  val save = pluginI18n.tr("habits.save")
  val cancel = pluginI18n.tr("habits.cancel")

  val archivedSuffix = pluginI18n.tr("habits.archivedSuffix")

  fun norm(amount: String) = pluginI18n.tr("habits.norm", "amount" to amount)

  fun monthGoal(amount: String) = pluginI18n.tr("habits.monthGoal", "amount" to amount)

  val moveUp = pluginI18n.tr("habits.moveUp")
  val moveDown = pluginI18n.tr("habits.moveDown")

  val newHabit = pluginI18n.tr("habits.newHabit")
  val name = pluginI18n.tr("habits.name")
  val dailyNorm = pluginI18n.tr("habits.dailyNorm")
  val monthGoalOptional = pluginI18n.tr("habits.monthGoalOptional")
  val archived = pluginI18n.tr("habits.archived")
  val deleteHabit = pluginI18n.tr("habits.deleteHabit")
  val deleteHabitQuestion = pluginI18n.tr("habits.deleteHabitQuestion")
  val deleteHabitText = pluginI18n.tr("habits.deleteHabitText")
  val icon = pluginI18n.tr("habits.icon")

  val periodForPercent = pluginI18n.tr("habits.periodForPercent")
  val year = pluginI18n.tr("habits.year")

  fun days(n: Int) = pluginI18n.tr("habits.days", "n" to n)

  val perfectStreak = pluginI18n.tr("habits.perfectStreak")

  fun record(best: Int) = pluginI18n.tr("habits.record", "best" to best)

  fun completionFor(period: Int) = pluginI18n.tr("habits.completionFor", "period" to period)

  fun todayDone(done: Int, total: Int) = pluginI18n.tr("habits.todayDone", "done" to done, "total" to total)

  val perfectInMonth = pluginI18n.tr("habits.perfectInMonth")
  val perfectInYear = pluginI18n.tr("habits.perfectInYear")

  fun goalPercent(goal: Int, pct: Int) = pluginI18n.tr("habits.goalPercent", "goal" to goal, "pct" to pct)

  val byHabit = pluginI18n.tr("habits.byHabit")
  val changePerfectGoals = pluginI18n.tr("habits.changePerfectGoals")
  val halfYearActivity = pluginI18n.tr("habits.halfYearActivity")

  fun trackedDays(n: Int) = pluginI18n.tr("habits.trackedDays", "n" to n)

  val less = pluginI18n.tr("habits.less")
  val more = pluginI18n.tr("habits.more")

  fun streak(n: Int) = pluginI18n.tr("habits.streak", "n" to n)

  fun best(n: Int) = pluginI18n.tr("habits.best", "n" to n)

  fun monthAndTotal(month: String, total: String) = pluginI18n.tr("habits.monthAndTotal", "month" to month, "total" to total)

  fun monthGoalProgress(
    month: String,
    goal: String,
    pct: Int
  ) = pluginI18n.tr("habits.monthGoalProgress", "month" to month, "goal" to goal, "pct" to pct)

  val byWeekday = pluginI18n.tr("habits.byWeekday")

  fun overDays(period: Int) = pluginI18n.tr("habits.overDays", "period" to period)

  val byMonth = pluginI18n.tr("habits.byMonth")
  val perfectDaysByMonth = pluginI18n.tr("habits.perfectDaysByMonth")
  val perfectGoals = pluginI18n.tr("habits.perfectGoals")
  val perfectGoalsHint = pluginI18n.tr("habits.perfectGoalsHint")
  val perMonth = pluginI18n.tr("habits.perMonth")
  val perYear = pluginI18n.tr("habits.perYear")
  val habitList = pluginI18n.tr("habits.habitList")
  val habitListHint = pluginI18n.tr("habits.habitListHint")
  val openHabitList = pluginI18n.tr("habits.openHabitList")
}
