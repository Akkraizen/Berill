package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.sync.*
import dev.akkraizen.berill.core.preferences.*
import dev.akkraizen.berill.core.notifications.*
import dev.akkraizen.berill.core.connections.*
import dev.akkraizen.berill.protocol.*
import kotlinx.serialization.json.*
import kotlin.test.*

class WorkspaceFeaturesTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val db = BerillDatabase(driver)
  init { BerillDatabase.Schema.create(driver) }
  private val store = LocalStore(db, HybridClock("local") { 100L })
  @AfterTest fun close() = driver.close()
  private fun base(): SyncRecord = SyncRecord("notes", "n", buildJsonObject { put("title", "Base"); put("body", "Body") }, mapOf("title" to HybridClock.encode(10, 0, "server"), "body" to HybridClock.encode(10, 0, "server")))
  private fun divergent(): SyncConflict {
    val base = base(); store.applyRemote(listOf(base))
    store.put("notes", "n", mapOf("title" to JsonPrimitive("Local")))
    val remote = base.copy(fields = buildJsonObject { put("title", "Remote"); put("body", "New server body") }, clocks = mapOf("title" to HybridClock.encode(200, 0, "server"), "body" to HybridClock.encode(200, 0, "server")))
    store.applyRemote(listOf(remote))
    return store.conflicts.state.value.single()
  }
  @Test fun overlappingEditsPreserveBothVersionsAcrossRestartAndResolveOnlyConflictingFields() {
    val conflict = divergent()
    assertEquals(listOf("title"), conflict.fields)
    assertEquals(JsonPrimitive("Local"), conflict.local.fields["title"])
    assertEquals(JsonPrimitive("Remote"), conflict.remote.fields["title"])
    val restarted = LocalStore(db, HybridClock("local") { 100L })
    assertEquals(conflict, restarted.conflicts.state.value.single())
    assertTrue(restarted.resolveConflict(conflict.id, ConflictChoice.LOCAL))
    val resolved = restarted.get("notes", "n")!!
    assertEquals(JsonPrimitive("Local"), resolved.fields["title"])
    assertEquals(JsonPrimitive("New server body"), resolved.fields["body"])
    assertTrue(resolved.clocks.getValue("title") > conflict.remote.clocks.getValue("title"))
    assertTrue(restarted.conflicts.state.value.isEmpty())
    assertEquals(1, restarted.dirty().size)
  }
  @Test fun laterEditsCannotBeOverwrittenByResolvingAnOldConflict() {
    val conflict = divergent()
    store.put("notes", "n", mapOf("title" to JsonPrimitive("Later")))
    assertFalse(store.resolveConflict(conflict.id, ConflictChoice.REMOTE))
    assertEquals(JsonPrimitive("Later"), store.get("notes", "n")!!.fields["title"])
    assertTrue(store.resolveConflict(conflict.id, ConflictChoice.MERGED))
    assertTrue(store.conflicts.state.value.isEmpty())
  }
  @Test fun disjointAndRepeatedRemoteEditsDoNotCreateConflicts() {
    val base = base(); store.applyRemote(listOf(base))
    store.put("notes", "n", mapOf("body" to JsonPrimitive("Local body")))
    val remote = base.copy(fields = JsonObject(base.fields + ("title" to JsonPrimitive("Remote"))), clocks = base.clocks + ("title" to HybridClock.encode(200, 0, "server")))
    store.applyRemote(listOf(remote)); store.applyRemote(listOf(remote))
    assertTrue(store.conflicts.state.value.isEmpty())
    assertEquals(JsonPrimitive("Local body"), store.get("notes", "n")!!.fields["body"])
    assertEquals(JsonPrimitive("Remote"), store.get("notes", "n")!!.fields["title"])
  }
  @Test fun reconciliationOfCleanDataIsNotMisreportedAsAConflict() {
    val base = base(); store.applyRemote(listOf(base)); store.enqueueAllForSync()
    val remote = base.copy(fields = JsonObject(base.fields + ("title" to JsonPrimitive("Remote"))), clocks = base.clocks + ("title" to HybridClock.encode(200, 0, "server")))
    store.applyRemote(listOf(remote))
    assertTrue(store.conflicts.state.value.isEmpty())
  }
  @Test fun staleRemotePagesDoNotConflictWithNewLocalEdits() {
    val initial = base(); store.applyRemote(listOf(initial))
    store.put("notes", "n", mapOf("title" to JsonPrimitive("Local")))
    val stale = initial.copy(fields = JsonObject(initial.fields + ("title" to JsonPrimitive("Older"))), clocks = initial.clocks + ("title" to HybridClock.encode(5, 0, "server")))
    store.applyRemote(listOf(stale))
    assertTrue(store.conflicts.state.value.isEmpty())
    assertEquals(JsonPrimitive("Local"), store.get("notes", "n")!!.fields["title"])
  }
  @Test fun managedTimestampsDoNotTurnIndependentNoteEditsIntoConflicts() {
    val initial = base().copy(fields = JsonObject(base().fields + ("updatedAt" to JsonPrimitive(10))), clocks = base().clocks + ("updatedAt" to HybridClock.encode(10, 0, "server")))
    store.applyRemote(listOf(initial))
    store.put("notes", "n", mapOf("body" to JsonPrimitive("Local body"), "updatedAt" to JsonPrimitive(100)))
    val remote = initial.copy(fields = JsonObject(initial.fields + mapOf("title" to JsonPrimitive("Remote title"), "updatedAt" to JsonPrimitive(200))), clocks = initial.clocks + mapOf("title" to HybridClock.encode(200, 0, "server"), "updatedAt" to HybridClock.encode(200, 0, "server")))
    store.applyRemote(listOf(remote))
    assertTrue(store.conflicts.state.value.isEmpty())
    assertEquals(JsonPrimitive("Local body"), store.get("notes", "n")!!.fields["body"])
    assertEquals(JsonPrimitive("Remote title"), store.get("notes", "n")!!.fields["title"])
  }
  @Test fun oldRemotePagesAndClockRestampingDoNotProduceFalseConflicts() {
    val base = base().copy(clocks = mapOf("title" to HybridClock.encode(200, 0, "server"), "body" to HybridClock.encode(200, 0, "server")))
    store.applyRemote(listOf(base)); store.enqueueAllForSync()
    store.applyRemote(listOf(this.base().copy(fields = JsonObject(base.fields + ("title" to JsonPrimitive("Older"))))))
    store.applyRemote(listOf(base.copy(fields = JsonObject(base.fields + ("title" to JsonPrimitive("Newer"))), clocks = base.clocks + ("title" to HybridClock.encode(300, 0, "server")))))
    assertTrue(store.conflicts.state.value.isEmpty())
    store.restampAll()
    store.applyRemote(listOf(base.copy(fields = JsonObject(base.fields + ("title" to JsonPrimitive("Newest"))), clocks = base.clocks + ("title" to HybridClock.encode(400, 0, "server")))))
    assertTrue(store.conflicts.state.value.isEmpty())
  }
  @Test fun purgingTrashErasesConflictSnapshotsToo() {
    divergent()
    store.delete("notes", "n")
    store.purgeDeleted(store.deletedRecords())
    assertTrue(store.conflicts.state.value.isEmpty())
    assertFalse(store.meta("sync.base.notes/n").orEmpty().contains("Local"))
    assertFalse(store.meta("sync.base.notes/n").orEmpty().contains("Remote"))
  }
  @Test fun deletionVersusEditIsExplicitAndCanBeResolvedAsDeletion() {
    val base = base(); store.applyRemote(listOf(base)); store.delete("notes", "n")
    store.applyRemote(listOf(base.copy(fields = JsonObject(base.fields + ("body" to JsonPrimitive("Remote"))), clocks = base.clocks + ("body" to HybridClock.encode(200, 0, "server")))))
    val conflict = store.conflicts.state.value.single()
    assertTrue(conflict.deletion)
    assertTrue(store.resolveConflict(conflict.id, ConflictChoice.LOCAL))
    assertTrue(store.get("notes", "n")!!.deleted)
  }
  @Test fun atomicMcpMutationsRejectStaleSnapshotsAndNeverRecreateDeletedRecords() {
    val fields = buildJsonObject { put("title", "Initial") }
    assertTrue(store.createIfAbsent("notes", "n", fields))
    assertFalse(store.createIfAbsent("notes", "n", fields))
    store.put("notes", "n", mapOf("title" to JsonPrimitive("Manual")))
    assertFalse(store.mutateIfCurrent("notes", "n", fields, buildJsonObject { put("title", "Agent") }, false))
    assertEquals(JsonPrimitive("Manual"), store.get("notes", "n")!!.fields["title"])
    store.delete("notes", "n")
    assertFalse(store.createIfAbsent("notes", "n", fields))
  }
  @Test fun notificationReadStatePersistsAndFailuresAreGroupedWithoutDroppingActions() {
    val center = NotificationCenter(store)
    center.report(FeatureProblem(FailureOperation.EXPORT, FailureReason.NETWORK))
    center.report(FeatureProblem(FailureOperation.EXPORT, FailureReason.NETWORK))
    assertEquals(1, center.state.value.size)
    center.markRead(center.state.value.single().id)
    val restarted = NotificationCenter(store)
    assertTrue(restarted.state.value.single().read)
    assertEquals(FailureOperation.EXPORT, restarted.state.value.single().operation)
    restarted.clearRead(); assertTrue(restarted.state.value.isEmpty())
  }
  @Test fun preferencesPersistResetAndKeepOrderingDeterministicWhenPluginsDisappear() {
    val preferences = LocalPreferences(store, "test.home", HomePreferences.serializer(), HomePreferences())
    preferences.set(HomePreferences(hiddenCards = setOf("books/reading"), cardOrder = listOf("books/reading", "core/notes")))
    assertEquals(preferences.state.value, LocalPreferences(store, "test.home", HomePreferences.serializer(), HomePreferences()).state.value)
    assertEquals(listOf("core/notes", "planner/today"), orderedKeys(listOf("core/notes", "planner/today"), preferences.state.value.cardOrder))
    assertEquals(listOf("b", "disabled", "a"), mergeOrder(listOf("a", "disabled", "b"), listOf("b", "a")))
    assertEquals(listOf("b", "a", "c"), moveKey(listOf("a", "b", "c"), "b", -1))
    preferences.reset(); assertEquals(HomePreferences(), preferences.state.value)
    val editor = LocalPreferences(store, "test.editor", EditorPreferences.serializer(), EditorPreferences())
    editor.set(EditorPreferences(true, 20, true))
    assertEquals(EditorPreferences(true, 20, true), LocalPreferences(store, "test.editor", EditorPreferences.serializer(), EditorPreferences()).state.value)
  }

  @Test fun editorDraftMergesDisjointChangesWithoutOverwritingExternalTitle() {
    val original = base(); store.applyRemote(listOf(original))
    store.put("notes", "n", mapOf("title" to JsonPrimitive("Agent title")))
    val desired = JsonObject(original.fields + ("body" to JsonPrimitive("User body")))
    val result = store.mergeDraft("notes", "n", original, desired)
    assertNull(result.conflictId)
    assertEquals(JsonPrimitive("Agent title"), result.record!!.fields["title"])
    assertEquals(JsonPrimitive("User body"), result.record!!.fields["body"])
  }
  @Test fun editorDraftPreservesOverlappingUnsavedTextAndUpdatesOneConflict() {
    val original = base(); store.applyRemote(listOf(original))
    store.put("notes", "n", mapOf("body" to JsonPrimitive("Agent body")))
    val result = store.mergeDraft("notes", "n", original, JsonObject(original.fields + ("body" to JsonPrimitive("User body"))))
    assertNotNull(result.conflictId)
    assertEquals(JsonPrimitive("Agent body"), store.get("notes", "n")!!.fields["body"])
    val updated = store.mergeDraft("notes", "n", result.baseline, JsonObject(original.fields + ("body" to JsonPrimitive("User continued"))), result.conflictId)
    assertEquals(result.conflictId, updated.conflictId)
    assertEquals(1, store.conflicts.state.value.size)
    assertTrue(store.resolveConflict(updated.conflictId!!, ConflictChoice.LOCAL))
    assertEquals(JsonPrimitive("User continued"), store.get("notes", "n")!!.fields["body"])
  }
  @Test fun editorDraftCannotSilentlyResurrectDeletedNote() {
    val original = base(); store.applyRemote(listOf(original)); store.delete("notes", "n")
    val result = store.mergeDraft("notes", "n", original, JsonObject(original.fields + ("body" to JsonPrimitive("Unsaved body"))))
    assertTrue(result.deletion)
    assertTrue(store.get("notes", "n")!!.deleted)
    assertTrue(store.resolveConflict(result.conflictId!!, ConflictChoice.LOCAL))
    assertFalse(store.get("notes", "n")!!.deleted)
    assertEquals(JsonPrimitive("Unsaved body"), store.get("notes", "n")!!.fields["body"])
  }
}
