package dev.akkraizen.berill.core

import dev.akkraizen.berill.core.sync.SyncOptions
import dev.akkraizen.berill.protocol.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncOptionsTest {
  @Test
  fun `по умолчанию синхронизируется всё`() {
    val o = SyncOptions()
    assertTrue(o.includes(Collections.SETTINGS, "appearance"))
    assertTrue(o.includes(Collections.SETTINGS, "plugin.books"))
    assertTrue(o.includes(Collections.NOTES, "n1"))
  }

  @Test
  fun `исключения по типу настройки и по коллекции`() {
    val o = SyncOptions(
      appearance = false,
      sections = false,
      pluginSettings = false,
      excludedCollections = setOf(Collections.NOTES)
    )
    assertFalse(o.includes(Collections.SETTINGS, "appearance"))
    assertFalse(o.includes(Collections.SETTINGS, "plugins"))
    assertFalse(o.includes(Collections.SETTINGS, "plugins_installed"))
    assertFalse(o.includes(Collections.SETTINGS, "plugin.books"))
    assertFalse(o.includes(Collections.NOTES, "n1"))
    assertTrue(o.includes("books", "b1"))
  }

  @Test
  fun `кодирование туда и обратно, мусор даёт значения по умолчанию`() {
    val o =
      SyncOptions(auto = false, intervalMinutes = 15, appearance = false, excludedCollections = setOf("books", "notes"))
    assertEquals(o, SyncOptions.decode(o.encode()))
    assertEquals(SyncOptions(), SyncOptions.decode("не json"))
    assertEquals(SyncOptions(), SyncOptions.decode(null))
  }

  @Test
  fun `повторное включение требует полной подкачки`() {
    val off = SyncOptions(appearance = false, excludedCollections = setOf("books"))
    assertTrue(SyncOptions().widens(off))
    assertFalse(off.widens(SyncOptions()))
    assertTrue(off.copy(excludedCollections = emptySet()).widens(off))
  }
}
