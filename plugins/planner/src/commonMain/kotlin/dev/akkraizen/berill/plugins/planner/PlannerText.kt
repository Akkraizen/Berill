package dev.akkraizen.berill.plugins.planner


internal object PlannerText {

  val title = pluginI18n.tr("planner.title")
  val description = pluginI18n.tr("planner.description")
  val todayPlans = pluginI18n.tr("planner.todayPlans")

  val deadlines = pluginI18n.tr("planner.deadlines")
  val showDeadlines = pluginI18n.tr("planner.showDeadlines")
  val showDeadlinesHint = pluginI18n.tr("planner.showDeadlinesHint")

  val previousWeek = pluginI18n.tr("planner.previousWeek")
  val nextWeek = pluginI18n.tr("planner.nextWeek")
  val thisWeek = pluginI18n.tr("planner.thisWeek")

  fun weekNumber(n: Int) = pluginI18n.tr("planner.weekNumber", "n" to n)

  val week = pluginI18n.tr("planner.week")
  val day = pluginI18n.tr("planner.day")

  val today = pluginI18n.tr("planner.today")
  val noTasks = pluginI18n.tr("planner.noTasks")

  fun percentDone(percent: Int) = pluginI18n.tr("planner.percentDone", "percent" to percent)

  val forWeek = pluginI18n.tr("planner.forWeek")
  val weekGoals = pluginI18n.tr("planner.weekGoals")
  val addGoalsHint = pluginI18n.tr("planner.addGoalsHint")
  val tails = pluginI18n.tr("planner.tails")
  val tailsHint = pluginI18n.tr("planner.tailsHint")

  val notDoneEarlier = pluginI18n.tr("planner.notDoneEarlier")
  val allToToday = pluginI18n.tr("planner.allToToday")
  val toToday = pluginI18n.tr("planner.toToday")

  fun deadline(title: String) = pluginI18n.tr("planner.deadline", "title" to title)

  val empty = pluginI18n.tr("planner.empty")
  val taskShort = pluginI18n.tr("planner.taskShort")
  val newTaskHint = pluginI18n.tr("planner.newTaskHint")
  val addTask = pluginI18n.tr("planner.addTask")
  val toTomorrow = pluginI18n.tr("planner.toTomorrow")
  val delete = pluginI18n.tr("planner.delete")

  val noGoals = pluginI18n.tr("planner.noGoals")
  val weekGoal = pluginI18n.tr("planner.weekGoal")
  val addGoal = pluginI18n.tr("planner.addGoal")
  val weekSummary = pluginI18n.tr("planner.weekSummary")
  val weekSummaryHint = pluginI18n.tr("planner.weekSummaryHint")
}
