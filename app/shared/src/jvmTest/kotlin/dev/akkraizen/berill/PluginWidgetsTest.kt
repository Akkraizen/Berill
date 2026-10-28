package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.collection
import dev.akkraizen.berill.api.ui.minusDays
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.nav.Destination
import dev.akkraizen.berill.core.widgets.WidgetInstance
import dev.akkraizen.berill.core.widgets.WidgetText
import dev.akkraizen.berill.plugins.books.BooksPlugin
import dev.akkraizen.berill.plugins.books.data.Book
import dev.akkraizen.berill.plugins.books.data.READING
import dev.akkraizen.berill.plugins.finance.FinancePlugin
import dev.akkraizen.berill.plugins.finance.data.EXPENSE
import dev.akkraizen.berill.plugins.finance.data.INCOME
import dev.akkraizen.berill.plugins.finance.data.Operation
import dev.akkraizen.berill.plugins.habits.HabitsPlugin
import dev.akkraizen.berill.plugins.habits.data.HabitDef
import dev.akkraizen.berill.plugins.languages.LanguagesPlugin
import dev.akkraizen.berill.plugins.languages.data.Word
import dev.akkraizen.berill.plugins.nutrition.NutritionPlugin
import dev.akkraizen.berill.plugins.nutrition.data.FoodEntry
import dev.akkraizen.berill.plugins.nutrition.data.WaterDay
import dev.akkraizen.berill.plugins.nutrition.data.WeightEntry
import dev.akkraizen.berill.plugins.planner.PlannerPlugin
import dev.akkraizen.berill.plugins.planner.data.CalendarEvent
import dev.akkraizen.berill.plugins.planner.data.PlannerTask
import dev.akkraizen.berill.plugins.recipes.RecipesPlugin
import dev.akkraizen.berill.plugins.recipes.data.Recipe
import dev.akkraizen.berill.plugins.study.StudyPlugin
import dev.akkraizen.berill.plugins.study.data.StudyTask
import dev.akkraizen.berill.plugins.watching.WatchingPlugin
import dev.akkraizen.berill.plugins.watching.data.Title
import dev.akkraizen.berill.plugins.watching.data.WATCHING
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import dev.akkraizen.berill.plugins.workouts.data.Exercise
import dev.akkraizen.berill.plugins.workouts.data.Program
import dev.akkraizen.berill.plugins.workouts.data.WorkoutDay
import dev.akkraizen.berill.plugins.workouts.data.WorkoutLog
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class PluginWidgetsTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private fun core(): BerillCore {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    return BerillCore(driver, BerillPlugins, scope)
  }

  @AfterTest
  fun tearDown() = scope.cancel()

  private suspend fun BerillCore.render(key: String, choice: String? = null, state: Map<String, String> = emptyMap()): WidgetContent {
    val content = widgets.content(WidgetInstance(key, choice, state))
    assertTrue(content.blocks.none { it == WidgetBlock.Text(WidgetText.failed(), WidgetTone.Muted) }, "$key failed to render")
    assertEquals(content, widgets.decode(widgets.encode(content)))
    return content
  }

  private inline fun <reified T : WidgetBlock> WidgetContent.block(): T = blocks.filterIsInstance<T>().first()

  @Test
  fun `все встроенные модули отдают виджеты из каталога Android`() {
    val keys = core().widgets.all.map { it.key }.toSet()
    assertEquals(ANDROID_CATALOG, keys)
  }

  @Test
  fun `каждый виджет рисуется на пустой базе`() =
    runBlocking {
      val c = core()
      c.notes.save("n1", "Список", "- [ ] пункт")
      c.widgets.all.forEach { entry ->
        val choice = if (c.widgets.needsChoice(entry.key)) c.widgets.choices(entry.key).firstOrNull()?.id else null
        val content = c.render(entry.key, choice)
        assertTrue(content.title.isNotBlank(), entry.key)
      }
    }

  @Test
  fun `привычка отмечается и снимается с виджета`() =
    runBlocking {
      val c = core()
      c.data.collection<HabitDef>(HabitsPlugin.HABITS).save("read", HabitDef(label = "Чтение", defaultDuration = 1, unit = "times"))
      c.data.collection<HabitDef>(HabitsPlugin.HABITS).save("run", HabitDef(label = "Бег", defaultDuration = 30, order = 1))
      val check = c.render("habits.today").block<WidgetBlock.Checks>().items.first()
      assertEquals(false, check.checked)
      c.widgets.perform(WidgetInstance("habits.today"), check.action)
      assertEquals(listOf(true, false), c.render("habits.today").block<WidgetBlock.Checks>().items.map { it.checked })
      val grid = c.render("habits.week").block<WidgetBlock.Grid>()
      assertEquals(7, grid.header.size)
      assertEquals(1f, grid.rows.first().cells.last())
      val summary = c.widgets.summaries().first { it.title == HabitsPlugin.title }
      assertEquals("12", summary.trailing?.filter { it.isDigit() })
      c.widgets.perform(WidgetInstance("habits.today"), check.action)
      assertEquals(false, c.render("habits.today").block<WidgetBlock.Checks>().items.first().checked)
    }

  @Test
  fun `задача плана отмечается с виджета`() =
    runBlocking {
      val c = core()
      val tasks = c.data.collection<PlannerTask>(PlannerPlugin.TASKS)
      tasks.save("t1", PlannerTask("09:30 Созвон", date = today().toString()))
      tasks.save("t2", PlannerTask("Завтра", date = today().plusDays(1).toString()))
      val checks = c.render("planner.today").block<WidgetBlock.Checks>().items
      assertEquals(listOf("Созвон"), checks.map { it.label })
      assertEquals("09:30", checks.single().trailing)
      c.widgets.perform(WidgetInstance("planner.today"), checks.single().action)
      assertEquals(true, tasks.get("t1")?.done)
    }

  @Test
  fun `календарь показывает месяц, выбирает день и листает месяцы`() =
    runBlocking {
      val c = core()
      val events = c.data.collection<CalendarEvent>(PlannerPlugin.EVENTS)
      events.save("e1", CalendarEvent(title = "Пара", date = today().toString(), start = "10:00", end = "11:30"))
      events.save("e2", CalendarEvent(title = "Встреча", date = today().plusDays(3).toString()))
      val instance = WidgetInstance("planner.calendar")
      val content = c.render("planner.calendar")
      val month = content.block<WidgetBlock.Month>()
      assertEquals(42, month.days.size)
      val todayCell = month.days.single { it.today }
      assertEquals(true, todayCell.selected)
      assertEquals(1, todayCell.events)
      assertEquals("10:00–11:30", content.block<WidgetBlock.Rows>().items.single().trailing)
      val other = month.days.first { it.inMonth && !it.today }
      val selected = c.widgets.perform(instance, other.action!!) as WidgetEffect.Refresh
      val after = c.render("planner.calendar", state = selected.state!!).block<WidgetBlock.Month>()
      assertEquals(other.label, after.days.single { it.selected }.label)
      val next = content.buttons.last().action
      val shifted = c.widgets.perform(instance, next) as WidgetEffect.Refresh
      assertNotEquals(content.title, c.render("planner.calendar", state = shifted.state!!).title)
      val agenda = c.render("planner.agenda")
      assertEquals(listOf("Пара", "Встреча"), agenda.blocks.filterIsInstance<WidgetBlock.Rows>().flatMap { r -> r.items.map { it.title } })
      c.widgets.perform(instance, agenda.buttons.single().action)
      assertEquals(Destination.Plugin(PlannerPlugin.id, "new/${today()}", PlannerPlugin.PAGE_CALENDAR), c.nav.stack.value.last())
    }

  @Test
  fun `вода прибавляется стаканом и не уходит в минус`() =
    runBlocking {
      val c = core()
      val buttons = c.render("nutrition.water").block<WidgetBlock.Buttons>().items
      assertEquals(2, buttons.size)
      c.widgets.perform(WidgetInstance("nutrition.water"), buttons.first().action)
      val water = c.data.collection<WaterDay>(NutritionPlugin.WATER)
      assertEquals(250, water.get(today().toString())?.ml)
      val minus = c.render("nutrition.water").block<WidgetBlock.Buttons>().items.last()
      c.widgets.perform(WidgetInstance("nutrition.water"), minus.action)
      c.widgets.perform(WidgetInstance("nutrition.water"), minus.action)
      assertEquals(0, water.get(today().toString())?.ml)
    }

  @Test
  fun `калории и вес считаются из дневника`() =
    runBlocking {
      val c = core()
      c.data.collection<FoodEntry>(NutritionPlugin.FOODS).save("f1", FoodEntry(today().toString(), name = "Каша", grams = 200.0, kcal100 = 100.0, protein100 = 5.0))
      val ring = c.render("nutrition.calories").block<WidgetBlock.Ring>()
      assertEquals("200", ring.metric.value)
      assertTrue((ring.metric.progress ?: 0f) > 0f)
      val weights = c.data.collection<WeightEntry>(NutritionPlugin.WEIGHTS)
      weights.save("w1", WeightEntry(today().minusDays(2).toString(), 80.0))
      weights.save("w2", WeightEntry(today().toString(), 79.2))
      assertEquals(listOf(80.0, 79.2), c.render("nutrition.weight").block<WidgetBlock.Sparkline>().values)
    }

  @Test
  fun `серия прибавляется с виджета`() =
    runBlocking {
      val c = core()
      val titles = c.data.collection<Title>(WatchingPlugin.TITLES)
      titles.save("s1", Title(title = "Сериал", status = WATCHING, episodes = 10, watched = 3))
      val row = c.render("watching.now").block<WidgetBlock.Rows>().items.single()
      c.widgets.perform(WidgetInstance("watching.now"), assertNotNull(row.button).action)
      assertEquals(4, titles.get("s1")?.watched)
    }

  @Test
  fun `книга в процессе видна на виджете и открывается`() =
    runBlocking {
      val c = core()
      c.data.collection<Book>(BooksPlugin.BOOKS).save("b1", Book(title = "Мастер и Маргарита", author = "Булгаков", status = READING, started = today().toString(), pages = 480))
      val row = c.render("books.reading").block<WidgetBlock.Rows>().items.single()
      assertTrue(row.subtitle!!.contains("Булгаков"))
      assertIs<WidgetEffect.OpenApp>(c.widgets.perform(WidgetInstance("books.reading"), row.action!!))
      assertEquals(Destination.Plugin(BooksPlugin.id, "book:b1"), c.nav.stack.value.last())
    }

  @Test
  fun `карточка слова открывается и листается`() =
    runBlocking {
      val c = core()
      val words = c.data.collection<Word>(LanguagesPlugin.WORDS)
      words.save("w1", Word(language = "en", word = "apple", translation = "яблоко"))
      words.save("w2", Word(language = "en", word = "pear", translation = "груша"))
      val instance = WidgetInstance("languages.card")
      val card = c.render("languages.card").block<WidgetBlock.Card>()
      assertNull(card.back)
      val reveal = c.render("languages.card").block<WidgetBlock.Buttons>().items.first()
      val revealed = c.widgets.perform(instance, reveal.action) as WidgetEffect.Refresh
      val shown = c.render("languages.card", state = revealed.state!!).block<WidgetBlock.Card>()
      assertEquals(card.front, shown.front)
      assertNotNull(shown.back)
      val next = c.render("languages.card", state = revealed.state!!).block<WidgetBlock.Buttons>().items.last()
      val moved = c.widgets.perform(instance.copy(state = revealed.state!!), next.action) as WidgetEffect.Refresh
      assertNotEquals(card.front, c.render("languages.card", state = moved.state!!).block<WidgetBlock.Card>().front)
      assertEquals(listOf("en"), c.widgets.choices("languages.card").map { it.id })
    }

  @Test
  fun `рецепт дня выбирается из избранного и меняется`() =
    runBlocking {
      val c = core()
      val recipes = c.data.collection<Recipe>(RecipesPlugin.RECIPES)
      recipes.save("r1", Recipe(title = "Борщ", favorite = true))
      recipes.save("r2", Recipe(title = "Плов", favorite = true))
      recipes.save("r3", Recipe(title = "Не избранное"))
      val first = c.render("recipes.day").block<WidgetBlock.Text>().text
      assertTrue(first in setOf("Борщ", "Плов"))
      val another = c.render("recipes.day").block<WidgetBlock.Buttons>().items.last()
      val effect = c.widgets.perform(WidgetInstance("recipes.day"), another.action) as WidgetEffect.Refresh
      val second = c.render("recipes.day", state = effect.state!!).block<WidgetBlock.Text>().text
      assertNotEquals(first, second)
      assertTrue(second in setOf("Борщ", "Плов"))
    }

  @Test
  fun `таймер отдыха запускается и останавливается`() =
    runBlocking {
      val c = core()
      val instance = WidgetInstance("workouts.rest")
      assertNull(c.render("workouts.rest").block<WidgetBlock.Countdown>().endsAtMillis)
      val preset = c.render("workouts.rest").block<WidgetBlock.Buttons>().items.first()
      val started = c.widgets.perform(instance, preset.action) as WidgetEffect.Refresh
      val endsAt = assertNotNull(started.refreshAtMillis)
      assertEquals(endsAt.toString(), started.state?.get("endsAt"))
      val running = c.render("workouts.rest", state = started.state!!)
      assertEquals(endsAt, running.block<WidgetBlock.Countdown>().endsAtMillis)
      val stop = running.block<WidgetBlock.Buttons>().items.last()
      val stopped = c.widgets.perform(instance.copy(state = started.state!!), stop.action) as WidgetEffect.Refresh
      assertEquals(emptyMap(), stopped.state)
    }

  @Test
  fun `следующая тренировка идёт по дням программы и запускается`() =
    runBlocking {
      val c = core()
      c.data.collection<Program>(WorkoutsPlugin.PROGRAMS).save(
        "p1",
        Program(
          title = "Фулбоди",
          days = listOf(
            WorkoutDay("День A", listOf(Exercise("Присед", sets = 3, reps = "5", weight = 100.0))),
            WorkoutDay("День B", listOf(Exercise("Жим", sets = 3, reps = "5", weight = 60.0)))
          ),
          createdAt = 1
        )
      )
      assertEquals("День A", c.render("workouts.next").title)
      c.data.collection<WorkoutLog>(WorkoutsPlugin.LOGS).save("l0", WorkoutLog(today().minusDays(1).toString(), "A", "program:p1:0", finished = true, createdAt = 5))
      val next = c.render("workouts.next")
      assertEquals("День B", next.title)
      val start = next.block<WidgetBlock.Buttons>().items.single()
      assertEquals(true, (start.action as WidgetAction.Run).opensApp)
      assertIs<WidgetEffect.OpenApp>(c.widgets.perform(WidgetInstance("workouts.next"), start.action))
      val logs = c.data.collection<WorkoutLog>(WorkoutsPlugin.LOGS).all()
      assertEquals(2, logs.size)
      assertEquals("program:p1:1", logs.first { it.id != "l0" }.value.source)
      assertTrue(c.render("workouts.next").blocks.any { it is WidgetBlock.Bar })
    }

  @Test
  fun `дедлайны собираются из учёбы`() =
    runBlocking {
      val c = core()
      c.data.collection<StudyTask>(StudyPlugin.TASKS).save("s1", StudyTask(title = "Курсовая", subject = "Сети", deadline = today().plusDays(2).toString()))
      c.data.collection<StudyTask>(StudyPlugin.TASKS).save("s2", StudyTask(title = "Сдано", deadline = today().toString(), status = "сдано"))
      val rows = c.render("study.deadlines").block<WidgetBlock.Rows>().items
      assertEquals(listOf("Сети: Курсовая"), rows.map { it.title })
      c.widgets.perform(WidgetInstance("study.deadlines"), rows.single().action!!)
      assertEquals(Destination.Plugin(StudyPlugin.id, "task:s1"), c.nav.stack.value.last())
    }

  @Test
  fun `финансы показывают свободные деньги и быстрые траты`() =
    runBlocking {
      val c = core()
      val ops = c.data.collection<Operation>(FinancePlugin.OPERATIONS)
      ops.save("o1", Operation(INCOME, 1000.0, "Зарплата", null, today().toString()))
      ops.save("o2", Operation(EXPENSE, 300.0, "Кафе и рестораны", null, today().toString()))
      val month = c.render("finance.month")
      assertEquals("Кафе и рестораны", month.block<WidgetBlock.Bar>().label)
      val free = month.block<WidgetBlock.Metrics>().items.first().value.filter { it.isDigit() }
      assertEquals("700", free)
      val quick = c.render("finance.quick").block<WidgetBlock.Buttons>().items
      assertEquals("Кафе и рестораны", quick.first().label)
      c.widgets.perform(WidgetInstance("finance.quick"), quick.first().action)
      assertEquals(Destination.Plugin(FinancePlugin.id, "add:Кафе и рестораны"), c.nav.stack.value.last())
    }

  @Test
  fun `сегодня собирает сводки модулей`() =
    runBlocking {
      val c = core()
      c.data.collection<HabitDef>(HabitsPlugin.HABITS).save("read", HabitDef(label = "Чтение"))
      c.data.collection<PlannerTask>(PlannerPlugin.TASKS).save("t1", PlannerTask("Дело", date = today().toString()))
      val rows = c.render("core.today").block<WidgetBlock.Rows>().items
      assertTrue(rows.size >= 3, rows.toString())
      assertTrue(rows.none { it.title == WidgetText.todayTitle() })
    }

  private companion object {
    val ANDROID_CATALOG = setOf(
      "core.today",
      "core.note_quick",
      "core.note_pinned",
      "core.journal",
      "habits.today",
      "habits.week",
      "planner.today",
      "planner.calendar",
      "planner.agenda",
      "workouts.next",
      "workouts.rest",
      "nutrition.calories",
      "nutrition.water",
      "nutrition.weight",
      "books.reading",
      "watching.now",
      "languages.card",
      "study.deadlines",
      "finance.month",
      "finance.quick",
      "recipes.day"
    )
  }
}
