package dev.akkraizen.berill.core.widgets

import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.ddMMyyyy
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetButton
import dev.akkraizen.berill.api.widgets.WidgetCheck
import dev.akkraizen.berill.api.widgets.WidgetChoice
import dev.akkraizen.berill.api.widgets.WidgetChoices
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetSize
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.api.ui.markdown.markdownPlainText
import dev.akkraizen.berill.core.notes.Note
import dev.akkraizen.berill.core.notes.NotesRepository
import dev.akkraizen.berill.core.notes.displayTitle
import dev.akkraizen.berill.core.notes.preview
import kotlinx.datetime.LocalDate

internal fun coreWidgets(notes: NotesRepository, summaries: suspend (String) -> List<WidgetRow>): List<WidgetProvider> =
  listOf(TodayWidget(summaries), QuickNoteWidget(notes), PinnedNoteWidget(notes), JournalWidget(notes))

private fun recentNotes(notes: NotesRepository) = notes.all()

private class TodayWidget(private val summaries: suspend (String) -> List<WidgetRow>) : WidgetProvider {
  override val id = "today"
  override val title: Tr = WidgetText.todayTitle
  override val description: Tr = WidgetText.todayDescription
  override val size = WidgetSize.Large

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val rows = summaries(WidgetHub.key(WidgetHub.CORE, id))
    return WidgetContent(
      title = title(),
      subtitle = today().dayMonth(),
      icon = WidgetIcon.Calendar,
      open = WidgetAction.OpenHome,
      blocks = if (rows.isEmpty()) emptyList() else listOf(WidgetBlock.Rows(rows)),
      empty = WidgetText.todayEmpty()
    )
  }
}

private class QuickNoteWidget(private val notes: NotesRepository) : WidgetProvider {
  override val id = "note_quick"
  override val title: Tr = WidgetText.quickNoteTitle
  override val description: Tr = WidgetText.quickNoteDescription
  override val size = WidgetSize.Small

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val recent = recentNotes(notes).take(RECENT)
    return WidgetContent(
      title = title(),
      icon = WidgetIcon.Note,
      open = WidgetAction.OpenNote(null),
      blocks = listOf(
        WidgetBlock.Buttons(listOf(WidgetButton(WidgetText.newNote(), WidgetAction.OpenNote(null), WidgetIcon.Add, primary = true))),
        WidgetBlock.Rows(
          recent.map { WidgetRow(it.value.displayTitle(), it.value.preview().ifBlank { null }, action = WidgetAction.OpenNote(it.id), icon = WidgetIcon.Note) }
        )
      ).filterNot { it is WidgetBlock.Rows && it.items.isEmpty() }
    )
  }

  private companion object {
    const val RECENT = 3
  }
}

internal data class NoteCheck(val line: Int, val text: String, val done: Boolean)

private val CHECK = Regex("""^\s*[-*+] \[([ xX])\] (.*)$""")

internal fun noteChecks(body: String): List<NoteCheck> =
  body.lines().mapIndexedNotNull { i, line ->
    CHECK.matchEntire(line)?.let { NoteCheck(i, it.groupValues[2], it.groupValues[1] != " ") }
  }

internal fun toggleNoteCheck(body: String, line: Int, text: String): String {
  val lines = body.lines().toMutableList()
  val index = line.takeIf { CHECK.matchEntire(lines.getOrNull(it) ?: "")?.groupValues?.get(2) == text }
    ?: lines.indexOfFirst { CHECK.matchEntire(it)?.groupValues?.get(2) == text }.takeIf { it >= 0 }
    ?: return body
  val match = CHECK.matchEntire(lines[index]) ?: return body
  val mark = if (match.groupValues[1] == " ") "x" else " "
  lines[index] = lines[index].replaceFirst("[${match.groupValues[1]}]", "[$mark]")
  return lines.joinToString("\n")
}

private class PinnedNoteWidget(private val notes: NotesRepository) : WidgetProvider {
  override val id = "note_pinned"
  override val title: Tr = WidgetText.pinnedTitle
  override val description: Tr = WidgetText.pinnedDescription
  override val size = WidgetSize.Medium

  override val choices = WidgetChoices(WidgetText.pinnedChoose) {
    recentNotes(notes).map { item ->
      val checks = noteChecks(item.value.body)
      WidgetChoice(
        item.id,
        item.value.displayTitle(),
        if (checks.isEmpty()) item.value.preview().take(80).ifBlank { null } else WidgetText.checklistProgress(checks.count { it.done }, checks.size)
      )
    }
  }

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val noteId = request.choice ?: return WidgetHub.notice(title(), WidgetText.chooseHint())
    val note = notes.get(noteId) ?: return WidgetHub.notice(title(), WidgetText.pinnedMissing())
    val checks = noteChecks(note.body)
    val blocks = if (checks.isNotEmpty())
      listOf(
        WidgetBlock.Checks(
          checks.map { check ->
            WidgetCheck(
              check.text,
              check.done,
              WidgetAction.Run(TOGGLE, mapOf(LINE to check.line.toString(), TEXT to check.text))
            )
          }
        )
      )
    else
      listOfNotNull(markdownPlainText(note.body).trim().take(PREVIEW).ifBlank { null }?.let { WidgetBlock.Text(it) })
    return WidgetContent(
      title = note.displayTitle(),
      subtitle = if (checks.isEmpty()) null else WidgetText.checklistProgress(checks.count { it.done }, checks.size),
      icon = WidgetIcon.Note,
      open = WidgetAction.OpenNote(noteId),
      blocks = blocks,
      empty = WidgetText.pinnedEmpty()
    )
  }

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect {
    val noteId = request.choice ?: return WidgetEffect.Refresh()
    if (action.id != TOGGLE) return WidgetEffect.Refresh()
    val note = notes.get(noteId) ?: return WidgetEffect.Refresh()
    val line = action.args[LINE]?.toIntOrNull() ?: return WidgetEffect.Refresh()
    val text = action.args[TEXT] ?: return WidgetEffect.Refresh()
    notes.save(noteId, note.title, toggleNoteCheck(note.body, line, text))
    return WidgetEffect.Refresh()
  }

  private companion object {
    const val TOGGLE = "toggle"
    const val LINE = "line"
    const val TEXT = "text"
    const val PREVIEW = 600
  }
}

internal const val MOOD_COUNT = 5
internal val MOOD_EMOJI = listOf("😞", "😕", "😐", "🙂", "😄")

internal fun journalNoteId(date: LocalDate) = "journal-$date"

internal fun journalMood(body: String): Int? {
  val first = body.lineSequence().firstOrNull()?.trim().orEmpty()
  return MOOD_EMOJI.indexOfFirst { first.startsWith(it) }.takeIf { it >= 0 }
}

internal fun journalLine(body: String): String? {
  val lines = body.lines()
  val rest = if (journalMood(body) != null) lines.drop(1) else lines
  return markdownPlainText(rest.joinToString("\n")).lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
}

internal fun withJournalMood(body: String, mood: Int, label: String): String {
  val line = "${MOOD_EMOJI[mood]} $label"
  val lines = body.lines()
  return if (journalMood(body) != null)
    (listOf(line) + lines.drop(1)).joinToString("\n")
  else if (body.isBlank())
    line + "\n\n"
  else
    line + "\n\n" + body
}

private class JournalWidget(private val notes: NotesRepository) : WidgetProvider {
  override val id = "journal"
  override val title: Tr = WidgetText.journalTitle
  override val description: Tr = WidgetText.journalDescription
  override val size = WidgetSize.Medium

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val date = today()
    val noteId = journalNoteId(date)
    val note = notes.get(noteId)
    val mood = note?.body?.let(::journalMood)
    val labels = WidgetText.moods
    val line = note?.body?.let(::journalLine)
    return WidgetContent(
      title = title(),
      subtitle = date.dayMonth(),
      icon = WidgetIcon.Mood,
      open = WidgetAction.OpenNote(noteId),
      blocks = listOf(
        WidgetBlock.Buttons(
          MOOD_EMOJI.mapIndexed { i, emoji ->
            WidgetButton(emoji, WidgetAction.Run(MOOD, mapOf(VALUE to i.toString())), selected = mood == i)
          }
        ),
        WidgetBlock.Text(
          line ?: mood?.let { labels.getOrNull(it) } ?: WidgetText.journalPrompt(),
          if (line == null) WidgetTone.Muted else WidgetTone.Normal
        ),
        WidgetBlock.Buttons(listOf(WidgetButton(WidgetText.journalWrite(), WidgetAction.OpenNote(noteId), WidgetIcon.Note)))
      )
    )
  }

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect {
    val value = action.args[VALUE]?.toIntOrNull()?.takeIf { it in 0 until MOOD_COUNT } ?: return WidgetEffect.Refresh()
    val date = today()
    val noteId = journalNoteId(date)
    val note = notes.get(noteId) ?: Note(title = WidgetText.journalNoteTitle(date.ddMMyyyy()))
    val label = WidgetText.moods.getOrNull(value).orEmpty()
    notes.save(noteId, note.title, withJournalMood(note.body, value, label).trimEnd() + if (journalLine(note.body) == null) "\n\n" else "")
    return WidgetEffect.Refresh()
  }

  private companion object {
    const val MOOD = "mood"
    const val VALUE = "value"
  }
}
