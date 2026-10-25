package dev.akkraizen.berill.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.Capability
import dev.akkraizen.berill.api.Deadline
import dev.akkraizen.berill.api.DeadlineSource
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.all
import dev.akkraizen.berill.api.set
import dev.akkraizen.berill.api.string
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.nav.Destination
import dev.akkraizen.berill.core.nav.Tab
import dev.akkraizen.berill.core.ui.theme.ThemeMode
import dev.akkraizen.berill.core.ui.theme.parseHexColor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive

private class TestPlugin(
  override val id: String,
  private val withDeadlines: Boolean = false,
  override val installedByDefault: Boolean = true,
  override val collections: List<String> = emptyList()
) : BerillPlugin {
  override val title = id
  override val icon = Icons.Outlined.Science

  @Composable
  override fun Screen(ctx: PluginContext) = Unit

  override fun provides(ctx: PluginContext): List<Capability> =
    if (!withDeadlines) {
      emptyList()
    } else {
      listOf(
        object : DeadlineSource {
          override fun deadlines() = flowOf(listOf(Deadline("Курсовая", LocalDate(2026, 12, 20), id)))
        }
      )
    }
}

class KernelTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private fun core(vararg plugins: BerillPlugin): BerillCore {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    return BerillCore(driver, plugins.toList(), scope)
  }

  @AfterTest
  fun tearDown() = scope.cancel()

  @Test
  fun `пустая новая заметка не сохраняется`() {
    val c = core()
    assertFalse(c.notes.save("n1", "", "  "))
    assertNull(c.notes.get("n1"))
  }

  @Test
  fun `заметка создаётся, меняется и удаляется`() {
    val c = core()
    assertTrue(c.notes.save("n1", "Покупки", "молоко"))
    assertEquals("молоко", c.notes.get("n1")?.body)
    assertFalse(c.notes.save("n1", "Покупки", "молоко"), "без изменений запись не трогаем")
    assertTrue(c.notes.save("n1", "Покупки", "молоко, хлеб"))
    assertEquals("молоко, хлеб", c.notes.get("n1")?.body)
    c.notes.delete("n1")
    assertNull(c.notes.get("n1"))
  }

  @Test
  fun `метку получают только изменённые поля`() {
    val c = core()
    c.store.put("t", "r", mapOf("a" to JsonPrimitive(1), "b" to JsonPrimitive(2)))
    val before = c.store.get("t", "r")!!.clocks
    c.store.put("t", "r", mapOf("a" to JsonPrimitive(1), "b" to JsonPrimitive(3)))
    val after = c.store.get("t", "r")!!.clocks
    assertEquals(before["a"], after["a"])
    assertNotEquals(before["b"], after["b"])
  }

  @Test
  fun `плагины включены по умолчанию и выключаются`() =
    runBlocking {
      val c = core(TestPlugin("habits"), TestPlugin("books"))
      assertEquals(listOf("books", "habits"), c.plugins.enabled.value.map { it.id })
      c.plugins.setEnabled("books", false)
      withTimeout(5_000) { c.plugins.enabled.first { list -> list.none { it.id == "books" } } }
      Unit
    }

  @Test
  fun `новый плагин попадает в каталог, установка и удаление`() =
    runBlocking {
      val c = core(TestPlugin("habits"), TestPlugin("recipes", installedByDefault = false))
      assertEquals(listOf("habits"), c.plugins.enabled.value.map { it.id })
      assertEquals(listOf("recipes"), c.plugins.available.value.map { it.id })
      c.plugins.install("recipes")
      withTimeout(5_000) { c.plugins.enabled.first { list -> list.any { it.id == "recipes" } } }
      withTimeout(5_000) { c.plugins.available.first { it.isEmpty() } }
      c.plugins.uninstall("habits")
      withTimeout(5_000) { c.plugins.available.first { list -> list.any { it.id == "habits" } } }
      withTimeout(5_000) { c.plugins.enabled.first { list -> list.none { it.id == "habits" } } }
      Unit
    }

  @Test
  fun `оформление сохраняется`() =
    runBlocking {
      val c = core()
      assertEquals(ThemeMode.System, c.appearance.state.value.mode)
      c.appearance.setMode(ThemeMode.Dark)
      c.appearance.setAccent("#1E63D6")
      val a = withTimeout(5_000) {
        c.appearance.state.first { it.mode == ThemeMode.Dark && it.accent == "#1E63D6" }
      }
      assertEquals("#1E63D6", a.accent)
      assertEquals(Color(0xFF1E63D6), parseHexColor(a.accent))
      assertNull(parseHexColor("#12345"))
    }

  @Test
  fun `повторяющиеся и зарезервированные id отклоняются`() {
    assertFailsWith<IllegalArgumentException> { core(TestPlugin("habits"), TestPlugin("habits")) }
    assertFailsWith<IllegalArgumentException> { core(TestPlugin("notes")) }
    assertFailsWith<IllegalArgumentException> { core(TestPlugin("Bad-Id")) }
  }

  @Test
  fun `возможности видны только у включённых плагинов`() =
    runBlocking {
      val c = core(TestPlugin("study", withDeadlines = true), TestPlugin("planner"))
      val planner = c.plugins.context(c.plugins.byId("planner")!!)
      assertEquals(1, planner.capabilities.all<DeadlineSource>().size)
      c.plugins.setEnabled("study", false)
      withTimeout(5_000) { c.plugins.enabled.first { list -> list.none { it.id == "study" } } }
      assertTrue(planner.capabilities.all<DeadlineSource>().isEmpty())
    }

  @Test
  fun `настройки плагина сохраняются`() {
    val c = core(TestPlugin("books"))
    val settings = c.plugins.context(c.plugins.byId("books")!!).settings
    settings.set("view", "grid")
    assertEquals("grid", settings.string("view"))
  }

  @Test
  fun `навигация - назад всегда ведёт на главную`() {
    val c = core()
    c.nav.selectTab(Tab.Notes)
    c.nav.openNote("a")
    c.nav.openNote("b")
    assertEquals(listOf(Destination.Home, Destination.Notes, Destination.Note("b")), c.nav.stack.value)
    assertTrue(c.nav.back())
    assertTrue(c.nav.back())
    assertFalse(c.nav.back())
    assertEquals(Destination.Home, c.nav.current)
  }
}
