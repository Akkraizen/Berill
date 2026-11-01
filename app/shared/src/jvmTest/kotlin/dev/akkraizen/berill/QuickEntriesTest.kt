package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.collection
import dev.akkraizen.berill.api.ui.state.*
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.notes.Note
import dev.akkraizen.berill.plugins.planner.PlannerPlugin
import dev.akkraizen.berill.plugins.planner.data.PlannerTask
import kotlinx.coroutines.*
import kotlin.test.*

class QuickEntriesTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val core: BerillCore
  private val errors = mutableListOf<Exception>()
  init { BerillDatabase.Schema.create(driver); core = BerillCore(driver, BerillPlugins, scope) }
  private fun entries() = QuickEntries(core.store::meta, core.store::setMeta) { errors += it }
  @AfterTest fun close() { scope.cancel(); driver.close() }

  @Test fun draftsSurviveReopeningAndRemainOutsideSyncedCollections() {
    val draft = QuickDraft(entries(), QuickEntryKind.NOTE, Note.serializer(), Note())
    draft.value = Note("Unfinished", "## Draft\nSome text")
    val reopened = QuickDraft(entries(), QuickEntryKind.NOTE, Note.serializer(), Note())
    assertEquals(draft.value, reopened.value)
    assertTrue(core.store.records().none { it.collection == "notes" })
    reopened.reset(Note())
    assertEquals(Note(), QuickDraft(entries(), QuickEntryKind.NOTE, Note.serializer(), Note()).value)
  }

  @Test fun templatesArePersistentDistinctRemovableAndSeparatedByEntryKind() {
    val first = entries()
    val template = Note("Daily", "## Plan\n- ")
    first.addTemplate(QuickEntryKind.NOTE, Note.serializer(), template)
    first.addTemplate(QuickEntryKind.NOTE, Note.serializer(), template)
    assertEquals(listOf(template), entries().templates(QuickEntryKind.NOTE, Note.serializer()))
    assertTrue(entries().templates(QuickEntryKind.TASK, Note.serializer()).isEmpty())
    entries().removeTemplate(QuickEntryKind.NOTE, Note.serializer(), template)
    assertTrue(entries().templates(QuickEntryKind.NOTE, Note.serializer()).isEmpty())
  }

  @Test fun saveMoreCreatesIndependentRecordsAndUndoRemovesOnlyLastInsertion() {
    val service = entries()
    val tasks = core.data.collection<PlannerTask>(PlannerPlugin.TASKS)
    val first = PlannerTask("First", "2026-10-29")
    val second = PlannerTask("Second", "2026-10-30")
    saveQuickEntry(service, tasks, "first", first)
    saveQuickEntry(service, tasks, "second", second)
    assertEquals(2, tasks.all().size)
    assertTrue(service.undo(service.saved!!))
    assertEquals(first, tasks.get("first"))
    assertNull(tasks.get("second"))
    assertTrue(core.store.get(PlannerPlugin.TASKS, "second")!!.deleted)
    assertNull(service.saved)
  }

  @Test fun repeatedMealStyleBatchIsOneUndoAndStaleNotificationCannotUndoOtherRecords() {
    val service = entries()
    val tasks = core.data.collection<PlannerTask>(PlannerPlugin.TASKS)
    saveQuickEntry(service, tasks, "older", PlannerTask("Older"))
    val stale = service.saved!!
    val batch = listOf("one" to PlannerTask("One"), "two" to PlannerTask("Two"))
    saveQuickBatch(service, tasks, batch)
    assertFalse(service.undo(stale))
    assertEquals(3, tasks.all().size)
    assertTrue(service.undo(service.saved!!))
    assertEquals(listOf("older"), tasks.all().map { it.id })
  }

  @Test fun failedBatchRollsBackItsOwnWritesAndPreservesExistingRecords() {
    val service = entries()
    val real = core.data.collection<PlannerTask>(PlannerPlugin.TASKS)
    real.save("existing", PlannerTask("Keep"))
    val failing = object : dev.akkraizen.berill.api.DataCollection<PlannerTask> by real {
      override fun save(id: String, value: PlannerTask) {
        if (id == "two") error("Disk unavailable")
        real.save(id, value)
      }
    }
    assertFailsWith<IllegalStateException> {
      saveQuickBatch(service, failing, listOf("one" to PlannerTask("One"), "two" to PlannerTask("Two")))
    }
    assertEquals(listOf("existing"), real.all().map { it.id })
    assertNull(service.saved)
  }

  @Test fun undoDoesNotDeleteAnEntryEditedAfterCreation() {
    val service = entries()
    val tasks = core.data.collection<PlannerTask>(PlannerPlugin.TASKS)
    val task = PlannerTask("Original", "2026-10-29")
    saveQuickEntry(service, tasks, "task", task)
    tasks.save("task", task.copy(text = "Edited", done = true))
    assertFalse(service.undo(service.saved!!))
    assertEquals("Edited", tasks.get("task")!!.text)
    assertTrue(tasks.get("task")!!.done)
  }

  @Test fun failedSaveDoesNotOfferUndoOrEraseDraft() {
    val service = entries()
    val draft = QuickDraft(service, QuickEntryKind.TASK, PlannerTask.serializer(), PlannerTask())
    draft.value = PlannerTask("Keep this", "not a date yet")
    val real = core.data.collection<PlannerTask>(PlannerPlugin.TASKS)
    val failing = object : dev.akkraizen.berill.api.DataCollection<PlannerTask> by real {
      override fun save(id: String, value: PlannerTask) { error("Disk unavailable") }
    }
    assertFailsWith<IllegalStateException> { saveQuickEntry(service, failing, "task", draft.value) }
    assertNull(service.saved)
    assertEquals(draft.value, QuickDraft(entries(), QuickEntryKind.TASK, PlannerTask.serializer(), PlannerTask()).value)
  }

  @Test fun corruptedDraftReportsErrorAndStorageFailureKeepsInMemoryInput() {
    val broken = QuickEntries({ "invalid-json" }, { _, _ -> error("Read only") }) { errors += it }
    val draft = QuickDraft(broken, QuickEntryKind.NOTE, Note.serializer(), Note())
    assertEquals(Note(), draft.value)
    draft.value = Note("Still here", "")
    assertEquals("Still here", draft.value.title)
    assertEquals(2, errors.size)
  }

  @Test fun cancellationIsNeverSwallowedAndPlannerProvidesQuickPopup() {
    val cancelled = QuickEntries({ throw CancellationException() }, { _, _ -> }) { fail("Cancellation reported as an error") }
    assertFailsWith<CancellationException> { cancelled.load(QuickEntryKind.NOTE, Note.serializer(), Note()) }
    val before = core.nav.current
    assertNotNull(PlannerPlugin.quickActions(core.plugins.context(PlannerPlugin)).single().popup)
    assertEquals(before, core.nav.current)
  }
}
