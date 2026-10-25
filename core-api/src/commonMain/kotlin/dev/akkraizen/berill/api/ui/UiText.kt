package dev.akkraizen.berill.api.ui

import dev.akkraizen.berill.api.i18n.Tr

object UiText {
  val cancel = Tr("ui.cancel")
  val delete = Tr("ui.delete")
  val save = Tr("ui.save")
  val add = Tr("ui.add")
  val search = Tr("ui.search")
  val find = Tr("ui.find")
  val close = Tr("ui.close")
  val empty = Tr("ui.empty")
  val today = Tr("ui.today")
  val tomorrow = Tr("ui.tomorrow")

  val previousMonth = Tr("ui.previousMonth")
  val nextMonth = Tr("ui.nextMonth")
  val thisMonth = Tr("ui.thisMonth")

  fun deleteWhat(what: String) = Tr("ui.deleteWhat", "what" to what)

  val deleteEverywhere = Tr("ui.deleteEverywhere")

  val datePlaceholder = Tr("ui.datePlaceholder")
  val bodyPlaceholder = Tr("ui.bodyPlaceholder")

  fun deleteItem(item: String) = Tr("ui.deleteItem", "item" to item)

  val resetList = Tr("ui.resetList")

  fun accessDenied(status: Int) = Tr("ui.accessDenied", "status" to status)

  val tooManyRequests = Tr("ui.tooManyRequests")

  fun serverResponded(status: Int) = Tr("ui.serverResponded", "status" to status)

  fun noConnection(details: String?) = Tr("ui.noConnection", "details" to details)

  val nothingFound = Tr("ui.nothingFound")

  fun networkNotAllowed(host: String) = Tr("ui.networkNotAllowed", "host" to host)

  val closeSearch = Tr("ui.closeSearch")

  val noDeadline = Tr("ui.noDeadline")

  fun overdueBy(days: Int) = Tr("ui.overdueBy", "days" to days, "p1" to if (days == 1) "day" else "days")

  val deadlineToday = Tr("ui.deadlineToday")
  val deadlineTomorrow = Tr("ui.deadlineTomorrow")

  fun inDays(days: Int) = Tr("ui.inDays", "days" to days, "p1" to if (days == 1) "day" else "days")

  fun minutes(m: Int) = Tr("ui.minutes", "m" to m)

  fun hours(h: Int) = Tr("ui.hours", "h" to h)

  fun hoursMinutes(h: Int, m: Int) = Tr("ui.hoursMinutes", "h" to h, "m" to m)
}
