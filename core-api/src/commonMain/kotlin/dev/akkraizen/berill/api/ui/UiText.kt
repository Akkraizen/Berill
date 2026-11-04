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
  val pickDate = Tr("ui.pickDate")
  val pickTime = Tr("ui.pickTime")
  val done = Tr("ui.done")
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

  val tabRecords = Tr("ui.tabRecords")
  val drag = Tr("ui.drag")
  val collapse = Tr("ui.collapse")
  val expand = Tr("ui.expand")
  val hide = Tr("ui.hide")
  val layoutBoard = Tr("ui.layoutBoard")
  val layoutCards = Tr("ui.layoutCards")
  val layoutRows = Tr("ui.layoutRows")
  val layoutDefault = Tr("ui.layoutDefault")
  val layoutChoose = Tr("ui.layoutChoose")
  val layoutGlobal = Tr("ui.layoutGlobal")
  val layoutGlobalHint = Tr("ui.layoutGlobalHint")
  val compactLayout = Tr("ui.compactLayout")
  val selectMany = Tr("ui.selectMany")
  val swipeDone = Tr("ui.swipeDone")
  val swipeUndo = Tr("ui.swipeUndo")
  val swipeOpen = Tr("ui.swipeOpen")
  val swipePin = Tr("ui.swipePin")
  val swipeUnpin = Tr("ui.swipeUnpin")
  val reorder = Tr("ui.reorder")
  val reorderHint = Tr("ui.reorderHint")
  val selectAll = Tr("ui.selectAll")
  val selectNone = Tr("ui.selectNone")
  val bulkStatus = Tr("ui.bulkStatus")
  val bulkDelete = Tr("ui.bulkDelete")
  fun selectedCount(n: Int) = Tr("ui.selectedCount", "n" to n)
  val compactLayoutHint = Tr("ui.compactLayoutHint")
  val layoutModule = Tr("ui.layoutModule")
  val tabStatistics = Tr("ui.tabStatistics")

  fun hours(h: Int) = Tr("ui.hours", "h" to h)

  fun hoursMinutes(h: Int, m: Int) = Tr("ui.hoursMinutes", "h" to h, "m" to m)
}
