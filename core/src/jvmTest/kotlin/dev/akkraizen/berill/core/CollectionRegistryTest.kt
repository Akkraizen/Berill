package dev.akkraizen.berill.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.plugin.CollectionRegistry
import dev.akkraizen.berill.protocol.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class DataPlugin(override val id: String, override val collections: List<String>) : BerillPlugin {
  override val title = id
  override val icon = Icons.Outlined.Science

  @Composable
  override fun Screen(ctx: PluginContext) = Unit
}

class CollectionRegistryTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @AfterTest
  fun tearDown() = scope.cancel()

  @Test
  fun `имена коллекций берутся из плагинов`() {
    val books = DataPlugin("books", listOf("books"))
    val habits = DataPlugin("habits", listOf("habits", "habit_days"))
    val r = CollectionRegistry(listOf(books, habits))
    assertEquals(listOf(Collections.NOTES, Collections.SETTINGS, "books", "habits", "habit_days"), r.all)
    assertEquals("habits", r.owner("habit_days")?.id)
    assertEquals(listOf("habits", "habit_days"), r.of(habits))
    assertNull(r.owner(Collections.NOTES))
    assertTrue(r.isCore(Collections.SETTINGS))
  }

  @Test
  fun `конфликты и некорректные имена отклоняются`() {
    assertFailsWith<IllegalArgumentException> {
      CollectionRegistry(listOf(DataPlugin("a", listOf("same")), DataPlugin("b", listOf("same"))))
    }
    assertFailsWith<IllegalArgumentException> {
      CollectionRegistry(listOf(DataPlugin("a", listOf(Collections.NOTES))))
    }
    assertFailsWith<IllegalArgumentException> {
      CollectionRegistry(listOf(DataPlugin("a", listOf("Bad-Name"))))
    }
  }

  @Test
  fun `плагин видит только свои коллекции`() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val mine = DataPlugin("mine", listOf("mine_items"))
    val other = DataPlugin("other", listOf("other_items"))
    val core = BerillCore(driver, listOf(mine, other), scope)
    val ctx = core.plugins.context(mine)
    ctx.data.raw("mine_items")
    assertFailsWith<IllegalArgumentException> { ctx.data.raw("other_items") }
    assertFailsWith<IllegalArgumentException> { ctx.data.raw(Collections.NOTES) }
  }
}
