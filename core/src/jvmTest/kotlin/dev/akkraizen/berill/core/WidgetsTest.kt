package dev.akkraizen.berill.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.nav.Destination
import dev.akkraizen.berill.core.widgets.WidgetInstance
import dev.akkraizen.berill.core.widgets.journalLine
import dev.akkraizen.berill.core.widgets.journalMood
import dev.akkraizen.berill.core.widgets.journalNoteId
import dev.akkraizen.berill.core.widgets.noteChecks
import dev.akkraizen.berill.core.widgets.toggleNoteCheck
import dev.akkraizen.berill.core.widgets.withJournalMood
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private class CounterWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "counter"
  override val title = Tr("widgets.appName")
  override val description = Tr("widgets.appName")

  override suspend fun content(request: WidgetRequest) =
    WidgetContent(title = "count", subtitle = ctx.settings.get("n")?.toString() ?: "0", blocks = listOf(WidgetBlock.Text(request.state["seen"] ?: "")))

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect =
    when (action.id) {
      "fail" -> error("broken")
      else -> WidgetEffect.Refresh(state = mapOf("seen" to action.args["value"].orEmpty()), refreshAtMillis = request.nowMillis + 1000)
    }

  override suspend fun summary(request: WidgetRequest) = WidgetRow("counter")
}

private class WidgetPlugin(override val id: String) : BerillPlugin {
  override val title = id
  override val icon = Icons.Outlined.Science
  override val installedByDefault = true

  @Composable
  override fun Screen(ctx: PluginContext) = Unit

  override fun widgets(ctx: PluginContext): List<WidgetProvider> = listOf(CounterWidget(ctx))
}

class WidgetsTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private fun core(vararg plugins: BerillPlugin): BerillCore {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    return BerillCore(driver, plugins.toList(), scope)
  }

  @AfterTest
  fun tearDown() = scope.cancel()

  @Test
  fun `виджеты ядра и плагинов получают ключи владельца`() {
    val c = core(WidgetPlugin("demo"))
    assertEquals(
      listOf("core.today", "core.note_quick", "core.note_pinned", "core.journal", "demo.counter"),
      c.widgets.all.map { it.key }
    )
  }

  @Test
  fun `состояние и время обновления возвращаются из действия`() =
    runBlocking {
      val c = core(WidgetPlugin("demo"))
      val effect = c.widgets.perform(WidgetInstance("demo.counter"), WidgetAction.Run("set", mapOf("value" to "7")))
      assertIs<WidgetEffect.Refresh>(effect)
      assertEquals(mapOf("seen" to "7"), effect.state)
      assertNotNull(effect.refreshAtMillis)
      val content = c.widgets.content(WidgetInstance("demo.counter", state = effect.state!!))
      assertEquals(WidgetBlock.Text("7"), content.blocks.single())
    }

  @Test
  fun `ошибка провайдера не роняет хост`() =
    runBlocking {
      val c = core(WidgetPlugin("demo"))
      assertIs<WidgetEffect.Refresh>(c.widgets.perform(WidgetInstance("demo.counter"), WidgetAction.Run("fail")))
      val missing = c.widgets.content(WidgetInstance("missing.widget"))
      assertEquals(WidgetTone.Muted, (missing.blocks.single() as WidgetBlock.Text).tone)
    }

  @Test
  fun `выключенный модуль скрывает виджет`() =
    runBlocking {
      val c = core(WidgetPlugin("demo"))
      c.plugins.setEnabled("demo", false)
      withTimeout(5_000) { c.plugins.enabled.first { list -> list.none { it.id == "demo" } } }
      assertTrue(c.widgets.available().none { it.key == "demo.counter" })
      val content = c.widgets.content(WidgetInstance("demo.counter"))
      assertEquals("demo", content.title)
      assertTrue(c.widgets.summaries().isEmpty())
    }

  @Test
  fun `открытие из виджета строит стек навигации`() =
    runBlocking {
      val c = core(WidgetPlugin("demo"))
      val effect = c.widgets.perform(WidgetInstance("demo.counter"), WidgetAction.Open(route = "item:1", page = "list"))
      assertIs<WidgetEffect.OpenApp>(effect)
      assertEquals(
        listOf(Destination.Home, Destination.Plugin("demo", page = "list"), Destination.Plugin("demo", "item:1", "list")),
        c.nav.stack.value
      )
      c.widgets.perform(WidgetInstance("core.note_quick"), WidgetAction.OpenNote("n1"))
      assertEquals(listOf(Destination.Home, Destination.Notes, Destination.Note("n1")), c.nav.stack.value)
    }

  @Test
  fun `содержимое и действия переживают сериализацию`() =
    runBlocking {
      val c = core(WidgetPlugin("demo"))
      val content = c.widgets.content(WidgetInstance("core.journal"))
      assertEquals(content, c.widgets.decode(c.widgets.encode(content)))
      val action = WidgetAction.Run("toggle", mapOf("line" to "2"), opensApp = true)
      assertEquals(action, c.widgets.decodeAction(c.widgets.encodeAction(action)))
      assertNull(c.widgets.decodeAction("{broken"))
    }

  @Test
  fun `закреплённая заметка переключает пункты чек-листа`() =
    runBlocking {
      val c = core()
      c.notes.save("list", "Покупки", "Список\n- [ ] молоко\n- [x] хлеб")
      assertTrue(c.widgets.needsChoice("core.note_pinned"))
      assertEquals(listOf("list"), c.widgets.choices("core.note_pinned").map { it.id })
      val instance = WidgetInstance("core.note_pinned", choice = "list")
      val checks = c.widgets.content(instance).blocks.filterIsInstance<WidgetBlock.Checks>().single().items
      assertEquals(listOf(false, true), checks.map { it.checked })
      c.widgets.perform(instance, checks.first().action)
      assertEquals("Список\n- [x] молоко\n- [x] хлеб", c.notes.get("list")?.body)
    }

  @Test
  fun `дневник дня сохраняет настроение в заметке дня`() =
    runBlocking {
      val c = core()
      val instance = WidgetInstance("core.journal")
      c.widgets.perform(instance, WidgetAction.Run("mood", mapOf("value" to "3")))
      val note = c.notes.get(journalNoteId(today()))
      assertNotNull(note)
      assertEquals(3, journalMood(note.body))
      c.notes.save(journalNoteId(today()), note.title, note.body.trimEnd() + "\n\nГулял в парке")
      c.widgets.perform(instance, WidgetAction.Run("mood", mapOf("value" to "4")))
      val updated = c.notes.get(journalNoteId(today()))!!.body
      assertEquals(4, journalMood(updated))
      assertEquals("Гулял в парке", journalLine(updated))
      val buttons = c.widgets.content(instance).blocks.filterIsInstance<WidgetBlock.Buttons>().first().items
      assertEquals(listOf(false, false, false, false, true), buttons.map { it.selected })
    }

  @Test
  fun `сводка дня собирает строки модулей`() =
    runBlocking {
      val c = core(WidgetPlugin("demo"))
      val content = c.widgets.content(WidgetInstance("core.today"))
      assertEquals(listOf("counter"), content.blocks.filterIsInstance<WidgetBlock.Rows>().single().items.map { it.title })
    }

  @Test
  fun `снимок для других платформ содержит все виджеты без обязательного выбора`() =
    runBlocking {
      val c = core(WidgetPlugin("demo"))
      val snapshot = c.widgets.snapshot()
      assertEquals(setOf("core.today", "core.note_quick", "core.journal", "demo.counter"), snapshot.widgets.keys)
      assertTrue(c.widgets.encodeSnapshot(snapshot).contains("\"demo.counter\""))
      val pinned = c.widgets.snapshot(listOf(WidgetInstance("core.note_pinned", choice = "n1")))
      assertEquals(setOf("core.note_pinned:n1"), pinned.widgets.keys)
    }

  @Test
  fun `разбор чек-листа и настроения`() {
    val body = "* [ ] a\ntext\n+ [X] b"
    assertEquals(listOf(0 to false, 2 to true), noteChecks(body).map { it.line to it.done })
    assertEquals("* [x] a\ntext\n+ [X] b", toggleNoteCheck(body, 0, "a"))
    assertEquals("* [ ] a\ntext\n+ [ ] b", toggleNoteCheck(body, 5, "b"))
    assertEquals(body, toggleNoteCheck(body, 0, "missing"))
    assertNull(journalMood("Обычный текст"))
    assertEquals("🙂 хорошо\n\nтекст", withJournalMood("😞 плохо\n\nтекст", 3, "хорошо"))
    assertEquals("😄 отлично\n\nтекст", withJournalMood("текст", 4, "отлично"))
  }
}
