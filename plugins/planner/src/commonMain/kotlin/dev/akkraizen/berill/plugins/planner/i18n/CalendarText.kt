package dev.akkraizen.berill.plugins.planner.i18n

internal object CalendarText {
  val pagePlan = pluginI18n.tr("planner.pagePlan")
  val pageCalendar = pluginI18n.tr("planner.pageCalendar")
  val previousDay = pluginI18n.tr("planner.previousDay")
  val nextDay = pluginI18n.tr("planner.nextDay")

  val event = pluginI18n.tr("planner.event")
  val newEvent = pluginI18n.tr("planner.newEvent")
  val eventTitle = pluginI18n.tr("planner.eventTitle")
  val allDay = pluginI18n.tr("planner.allDay")
  val startDate = pluginI18n.tr("planner.startDate")
  val endDate = pluginI18n.tr("planner.endDate")
  val startTime = pluginI18n.tr("planner.startTime")
  val endTime = pluginI18n.tr("planner.endTime")
  val timePlaceholder = pluginI18n.tr("planner.timePlaceholder")
  val location = pluginI18n.tr("planner.location")
  val notes = pluginI18n.tr("planner.notes")
  val calendar = pluginI18n.tr("planner.calendar")
  val onlyBerill = pluginI18n.tr("planner.onlyBerill")
  val endBeforeStart = pluginI18n.tr("planner.endBeforeStart")
  val deleteEvent = pluginI18n.tr("planner.deleteEvent")
  val deleteEventQuestion = pluginI18n.tr("planner.deleteEventQuestion")
  val deleteEventText = pluginI18n.tr("planner.deleteEventText")

  val google = pluginI18n.tr("planner.google")
  val googleHint = pluginI18n.tr("planner.googleHint")
  val googleUnavailable = pluginI18n.tr("planner.googleUnavailable")
  val signIn = pluginI18n.tr("planner.signIn")
  val waitingBrowser = pluginI18n.tr("planner.waitingBrowser")
  val signOut = pluginI18n.tr("planner.signOut")
  val syncNow = pluginI18n.tr("planner.syncNow")
  val syncing = pluginI18n.tr("planner.syncing")
  val showCalendars = pluginI18n.tr("planner.showCalendars")
  val defaultCalendar = pluginI18n.tr("planner.defaultCalendar")
  val readOnly = pluginI18n.tr("planner.readOnly")

  fun connectedAs(email: String) = pluginI18n.tr("planner.connectedAs", "email" to email)

  fun lastSync(time: String) = pluginI18n.tr("planner.lastSync", "time" to time)

  fun syncError(message: String) = pluginI18n.tr("planner.syncError", "message" to message)

  fun signInError(message: String) = pluginI18n.tr("planner.signInError", "message" to message)
}
