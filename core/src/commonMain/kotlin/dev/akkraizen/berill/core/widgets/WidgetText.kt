package dev.akkraizen.berill.core.widgets

import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.i18n.Tr

object WidgetText {
  val appName = Tr("widgets.appName")
  val unavailable = Tr("widgets.unavailable")
  val unavailableHint = Tr("widgets.unavailableHint")
  val moduleDisabled = Tr("widgets.moduleDisabled")
  val chooseHint = Tr("widgets.chooseHint")
  val failed = Tr("widgets.failed")
  val loading = Tr("widgets.loading")

  val pickerTitle = Tr("widgets.pickerTitle")
  val pickerHint = Tr("widgets.pickerHint")
  val pickerEmpty = Tr("widgets.pickerEmpty")
  val pickerChoose = Tr("widgets.pickerChoose")
  val pickerBack = Tr("widgets.pickerBack")
  val pickerNoChoices = Tr("widgets.pickerNoChoices")
  val pickerAny = Tr("widgets.pickerAny")

  val todayTitle = Tr("widgets.todayTitle")
  val todayDescription = Tr("widgets.todayDescription")
  val todayEmpty = Tr("widgets.todayEmpty")

  val quickNoteTitle = Tr("widgets.quickNoteTitle")
  val quickNoteDescription = Tr("widgets.quickNoteDescription")
  val newNote = Tr("widgets.newNote")
  val noNotes = Tr("widgets.noNotes")

  val pinnedTitle = Tr("widgets.pinnedTitle")
  val pinnedDescription = Tr("widgets.pinnedDescription")
  val pinnedChoose = Tr("widgets.pinnedChoose")
  val pinnedMissing = Tr("widgets.pinnedMissing")
  val pinnedEmpty = Tr("widgets.pinnedEmpty")

  val journalTitle = Tr("widgets.journalTitle")
  val journalDescription = Tr("widgets.journalDescription")
  val journalPrompt = Tr("widgets.journalPrompt")
  val journalWrite = Tr("widgets.journalWrite")

  fun journalNoteTitle(date: String) = I18n.text("widgets.journalNoteTitle", "date" to date)

  fun checklistProgress(done: Int, total: Int) = I18n.text("widgets.checklistProgress", "done" to done, "total" to total)

  val moods: List<String>
    get() = I18n.list("widgets.moods")
}
