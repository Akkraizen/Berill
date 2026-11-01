package dev.akkraizen.berill.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.ui.interaction.*
import dev.akkraizen.berill.core.connections.ShortcutSettings
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.protocol.HybridClock
import kotlin.test.*

class ShortcutsTest {
 @Test fun modifiersMatchExactlyAndPrimaryCoversBothPlatforms() {
  val chord = ShortcutAction.SEARCH.defaultBinding!!
  assertTrue(chord.matches(ShortcutKey.K, true, false, false, false))
  assertTrue(chord.matches(ShortcutKey.K, false, false, false, true))
  assertFalse(chord.matches(ShortcutKey.K, true, false, true, false))
  assertFalse(chord.matches(ShortcutKey.K, true, true, false, false))
  assertFalse(chord.matches(ShortcutKey.K, false, false, false, false))
 }
 @Test fun validatesBindingsAndDetectsDefaultAndGlobalConflicts() {
  val profile = ShortcutProfile()
  assertEquals(ShortcutAction.BOLD, profile.conflict(ShortcutAction.SEARCH, ShortcutChord(ShortcutKey.B, ctrl = true)))
  assertNull(profile.conflict(ShortcutAction.SEARCH, ShortcutChord(ShortcutKey.P, ctrl = true, shift = true)))
  assertFalse(ShortcutChord(ShortcutKey.A).valid)
  assertTrue(ShortcutChord(ShortcutKey.F5).valid)
  assertTrue(ShortcutChord(ShortcutKey.A, ctrl = true).valid)
  assertTrue(ShortcutChord.parseLabel("Cmd+Option+Space")!!.overlaps(ShortcutChord(ShortcutKey.SPACE, meta = true, alt = true)))
  assertEquals("Ctrl+Alt+B", ShortcutChord.parseLabel("Ctrl+Alt+B")!!.label())
 }
 @Test fun savesDisableResetAndConflictsWithoutTouchingRecords() {
  val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  try {
   BerillDatabase.Schema.create(driver)
   val store = LocalStore(BerillDatabase(driver), HybridClock("shortcuts") { 1L })
   val settings = ShortcutSettings(store)
   val chosen = ShortcutChord(ShortcutKey.P, ctrl = true, shift = true)
   settings.set(ShortcutAction.SEARCH, chosen)
   assertEquals(chosen, ShortcutSettings(store).state.value.binding(ShortcutAction.SEARCH))
   assertFailsWith<IllegalArgumentException> { settings.set(ShortcutAction.NEW_NOTE, chosen) }
   assertEquals(chosen, settings.state.value.binding(ShortcutAction.SEARCH))
   settings.set(ShortcutAction.SEARCH, null)
   assertNull(ShortcutSettings(store).state.value.binding(ShortcutAction.SEARCH))
   settings.set(ShortcutAction.DAY, ShortcutChord(ShortcutKey.K, ctrl = true))
   assertFailsWith<IllegalArgumentException> { settings.reset(ShortcutAction.SEARCH) }
   settings.resetAll()
   assertEquals(ShortcutProfile(), ShortcutSettings(store).state.value)
   assertEquals(0, store.countAll().toInt())
   store.setMeta("shortcuts.profile", "corrupt JSON")
   assertEquals(ShortcutProfile(), ShortcutSettings(store).state.value)
  } finally { driver.close() }
 }

 @Test fun addingFocusShortcutPreservesPreviouslyAssignedCombination() {
  val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  try {
   BerillDatabase.Schema.create(driver)
   val store = LocalStore(BerillDatabase(driver), HybridClock("shortcuts") { 1L })
   store.setMeta("shortcuts.profile", """{"overrides":{"SEARCH":{"key":"F","shift":true,"primary":true}}}""")
   val profile = ShortcutSettings(store).state.value
   assertEquals(ShortcutChord(ShortcutKey.F, shift = true, primary = true), profile.binding(ShortcutAction.SEARCH))
   assertNull(profile.binding(ShortcutAction.FOCUS))
  } finally { driver.close() }
 }
}
