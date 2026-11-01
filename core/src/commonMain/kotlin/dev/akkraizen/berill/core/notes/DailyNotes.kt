package dev.akkraizen.berill.core.notes

import dev.akkraizen.berill.api.ui.DAYS_FULL
import dev.akkraizen.berill.api.ui.ddMMyyyy
import dev.akkraizen.berill.api.ui.weekdayIndex
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.widgets.WidgetText
import dev.akkraizen.berill.core.widgets.journalLine
import dev.akkraizen.berill.core.widgets.journalMood
import dev.akkraizen.berill.core.widgets.journalNoteId
import dev.akkraizen.berill.core.widgets.withJournalMood
import dev.akkraizen.berill.protocol.Collections
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive

/**
 * The daily note (`journal-YYYY-MM-DD`) shared by Day, the journal widget and search: one title
 * format, an optional template for new notes (synced between devices), and the mood line.
 */
class DailyNotes(private val notes: NotesRepository, private val store: LocalStore) {
  fun id(date: LocalDate): String = journalNoteId(date)

  /** Markdown used for new daily notes; `{{date}}`, `{{weekday}}` and `{{iso}}` are replaced. */
  fun template(): String =
    (store.get(Collections.SETTINGS, TEMPLATE_RECORD)?.takeUnless { it.deleted }?.fields?.get(TEMPLATE_FIELD) as? JsonPrimitive)?.content.orEmpty()

  fun setTemplate(value: String) {
    store.put(Collections.SETTINGS, TEMPLATE_RECORD, mapOf(TEMPLATE_FIELD to JsonPrimitive(value)))
  }

  /** Creates the note for [date] from the template if it does not exist yet; returns its id. */
  fun ensure(date: LocalDate): String {
    val id = id(date)
    if (notes.get(id) == null) notes.save(id, WidgetText.journalNoteTitle(date.ddMMyyyy()), render(template(), date))
    return id
  }

  fun mood(date: LocalDate): Int? = notes.get(id(date))?.body?.let(::journalMood)

  fun setMood(date: LocalDate, mood: Int) {
    val label = WidgetText.moods.getOrNull(mood) ?: return
    val note = notes.get(ensure(date)) ?: return
    val body = withJournalMood(note.body, mood, label).trimEnd() + if (journalLine(note.body) == null) "\n\n" else ""
    notes.save(id(date), note.title, body)
  }

  companion object {
    const val TEMPLATE_RECORD = "daily_note"
    private const val TEMPLATE_FIELD = "template"

    fun render(template: String, date: LocalDate, weekdays: List<String> = DAYS_FULL): String =
      template
        .replace("{{date}}", date.ddMMyyyy())
        .replace("{{iso}}", date.toString())
        .replace("{{weekday}}", weekdays.getOrElse(date.weekdayIndex) { "" })
  }
}
