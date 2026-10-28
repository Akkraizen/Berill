package dev.akkraizen.berill.plugins.study.i18n

import dev.akkraizen.berill.plugins.study.data.DOING
import dev.akkraizen.berill.plugins.study.data.SUBMITTED
import dev.akkraizen.berill.plugins.study.data.TODO

internal object StudyText {

  val title = pluginI18n.tr("study.title")
  val description = pluginI18n.tr("study.description")
  val deadlines = pluginI18n.tr("study.deadlines")
  val noDeadlines = pluginI18n.tr("study.noDeadlines")

  val taskTypes = pluginI18n.tr("study.taskTypes")
  val newType = pluginI18n.tr("study.newType")

  private val statusTodo = pluginI18n.tr("study.statusTodo")
  private val statusDoing = pluginI18n.tr("study.statusDoing")
  private val statusSubmitted = pluginI18n.tr("study.statusSubmitted")

  fun statusLabel(status: String): String =
    when (status) {
      TODO -> statusTodo()
      DOING -> statusDoing()
      SUBMITTED -> statusSubmitted()
      else -> status
    }

  fun statusDescription(status: String) = pluginI18n.tr("study.statusDescription", "p0" to statusLabel(status))

  fun controlLabel(control: String): String =
    when (control) {
      "экзамен" -> exam()
      "зачёт" -> credit()
      else -> control.replaceFirstChar(Char::uppercase)
    }

  val statActive = pluginI18n.tr("study.statActive")
  val statOverdue = pluginI18n.tr("study.statOverdue")
  val statThisWeek = pluginI18n.tr("study.statThisWeek")
  val statSubmitted = pluginI18n.tr("study.statSubmitted")
  val allSubjects = pluginI18n.tr("study.allSubjects")
  val noActiveTasks = pluginI18n.tr("study.noActiveTasks")

  fun submittedCount(n: Int) = pluginI18n.tr("study.submittedCount", "n" to n)

  val newTask = pluginI18n.tr("study.newTask")

  fun grade(value: String) = pluginI18n.tr("study.grade", "value" to value)

  val submittedShort = pluginI18n.tr("study.submittedShort")

  val noSubject = pluginI18n.tr("study.noSubject")
  val whatToDo = pluginI18n.tr("study.whatToDo")
  val subject = pluginI18n.tr("study.subject")
  val type = pluginI18n.tr("study.type")
  val deadline = pluginI18n.tr("study.deadline")
  val enterTaskName = pluginI18n.tr("study.enterTaskName")
  val add = pluginI18n.tr("study.add")

  val subjects = pluginI18n.tr("study.subjects")

  fun subjectsSummary(count: Int, zet: Int, exams: Int, credits: Int) = pluginI18n.tr("study.subjectsSummary.4", "count" to count, "zet" to zet, "exams" to exams, "credits" to credits, "p4" to pluginI18n.plural("study.subjectsSummary", count), "p5" to pluginI18n.plural("study.subjectsSummary.2", zet), "p6" to pluginI18n.plural("study.subjectsSummary.3", exams))

  val noSubjects = pluginI18n.tr("study.noSubjects")
  val open = pluginI18n.tr("study.open")

  fun zetBadge(n: Int) = pluginI18n.tr("study.zetBadge.2", "n" to n, "p1" to pluginI18n.plural("study.zetBadge", n))

  val exam = pluginI18n.tr("study.exam")
  val credit = pluginI18n.tr("study.credit")
  val elective = pluginI18n.tr("study.elective")
  val lecturesShort = pluginI18n.tr("study.lecturesShort")
  val practiceShort = pluginI18n.tr("study.practiceShort")
  val labsShort = pluginI18n.tr("study.labsShort")
  val selfStudyShort = pluginI18n.tr("study.selfStudyShort")
  val noTasksLower = pluginI18n.tr("study.noTasksLower")

  fun openClosed(open: Int, closed: Int) = pluginI18n.tr("study.openClosed", "open" to open, "closed" to closed)

  val newSubject = pluginI18n.tr("study.newSubject")
  val subjectName = pluginI18n.tr("study.subjectName")
  val create = pluginI18n.tr("study.create")
  val cancel = pluginI18n.tr("study.cancel")

  val taskDeleted = pluginI18n.tr("study.taskDeleted")
  val untitled = pluginI18n.tr("study.untitled")
  val taskNoun = pluginI18n.tr("study.taskNoun")
  val taskAndNotes = pluginI18n.tr("study.taskAndNotes")
  val taskBodyPlaceholder = pluginI18n.tr("study.taskBodyPlaceholder")
  val properties = pluginI18n.tr("study.properties")
  val name = pluginI18n.tr("study.name")
  val gradeLabel = pluginI18n.tr("study.gradeLabel")

  val subjectDeleted = pluginI18n.tr("study.subjectDeleted")
  val subjectNoun = pluginI18n.tr("study.subjectNoun")
  val tasks = pluginI18n.tr("study.tasks")
  val noTasks = pluginI18n.tr("study.noTasks")
  val notes = pluginI18n.tr("study.notes")
  val subjectNotesPlaceholder = pluginI18n.tr("study.subjectNotesPlaceholder")
  val electiveSubject = pluginI18n.tr("study.electiveSubject")
  val teacher = pluginI18n.tr("study.teacher")
  val zet = pluginI18n.tr("study.zet")
  val semester = pluginI18n.tr("study.semester")
  val lecturesHours = pluginI18n.tr("study.lecturesHours")
  val practiceHours = pluginI18n.tr("study.practiceHours")
  val labsHours = pluginI18n.tr("study.labsHours")
  val selfStudyHours = pluginI18n.tr("study.selfStudyHours")

  val widgetDeadlinesTitle = pluginI18n.tr("study.widget.deadlinesTitle")
  val widgetDeadlinesDescription = pluginI18n.tr("study.widget.deadlinesDescription")
}
