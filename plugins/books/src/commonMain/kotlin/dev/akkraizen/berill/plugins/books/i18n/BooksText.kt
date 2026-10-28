package dev.akkraizen.berill.plugins.books.i18n

import dev.akkraizen.berill.plugins.books.data.DONE
import dev.akkraizen.berill.plugins.books.data.NOT_STARTED
import dev.akkraizen.berill.plugins.books.data.READING
import dev.akkraizen.berill.plugins.books.data.books

internal object BooksText {

  val title = pluginI18n.tr("books.title")
  val description = pluginI18n.tr("books.description")
  val readingNowCard = pluginI18n.tr("books.readingNowCard")
  val nothingReading = pluginI18n.tr("books.nothingReading")
  val bookFallback = pluginI18n.tr("books.bookFallback")

  val statusNotStarted = pluginI18n.tr("books.statusNotStarted")
  val statusReading = pluginI18n.tr("books.statusReading")
  val statusDone = pluginI18n.tr("books.statusDone")

  fun statusLabel(status: String): String =
    when (status) {
      NOT_STARTED -> statusNotStarted()
      READING -> statusReading()
      DONE -> statusDone()
      else -> status
    }

  fun readIn(year: Int) = pluginI18n.tr("books.readIn", "year" to year)

  val board = pluginI18n.tr("books.board")
  val findBook = pluginI18n.tr("books.findBook")
  val manually = pluginI18n.tr("books.manually")
  val sectionSettings = pluginI18n.tr("books.sectionSettings")
  val noBooks = pluginI18n.tr("books.noBooks")

  val newBook = pluginI18n.tr("books.newBook")
  val name = pluginI18n.tr("books.name")
  val author = pluginI18n.tr("books.author")
  val add = pluginI18n.tr("books.add")
  val cancel = pluginI18n.tr("books.cancel")

  val bookDeleted = pluginI18n.tr("books.bookDeleted")
  val untitled = pluginI18n.tr("books.untitled")
  val deleteWhat = pluginI18n.tr("books.deleteWhat")
  val properties = pluginI18n.tr("books.properties")
  val genre = pluginI18n.tr("books.genre")
  val pages = pluginI18n.tr("books.pages")
  val rating = pluginI18n.tr("books.rating")
  val started = pluginI18n.tr("books.started")
  val finished = pluginI18n.tr("books.finished")
  val coverLink = pluginI18n.tr("books.coverLink")
  val notes = pluginI18n.tr("books.notes")
  val notesPlaceholder = pluginI18n.tr("books.notesPlaceholder")

  val searchTitle = pluginI18n.tr("books.searchTitle")
  val searchPlaceholder = pluginI18n.tr("books.searchPlaceholder")

  fun alreadyInJournal(title: String) = pluginI18n.tr("books.alreadyInJournal", "title" to title)

  fun added(title: String, status: String) = pluginI18n.tr("books.added", "title" to title, "status" to status)

  val addAs = pluginI18n.tr("books.addAs")

  fun pagesShort(n: Int) = pluginI18n.tr("books.pagesShort", "n" to n)

  fun bodyTemplate(description: String): String = pluginI18n.text("books.bodyTemplate", "description" to description)

  val rateLimited = pluginI18n.tr("books.rateLimited")

  val settingsHint = pluginI18n.tr("books.settingsHint")
  val apiKeyOptional = pluginI18n.tr("books.apiKeyOptional")
  val keyStorageHint = pluginI18n.tr("books.keyStorageHint")

  val widgetReadingTitle = pluginI18n.tr("books.widget.readingTitle")
  val widgetReadingDescription = pluginI18n.tr("books.widget.readingDescription")

  fun widgetReadingDays(n: Int) = pluginI18n.tr("books.widget.readingDays", "n" to n)

  fun widgetPages(n: Int) = pluginI18n.tr("books.widget.pages", "n" to n)
}
