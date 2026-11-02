package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.widgets.*
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.nav.Destination
import dev.akkraizen.berill.core.widgets.WidgetInstance
import dev.akkraizen.berill.plugins.books.BooksPlugin
import dev.akkraizen.berill.plugins.books.data.Book
import dev.akkraizen.berill.plugins.recipes.RecipesPlugin
import dev.akkraizen.berill.plugins.recipes.data.Recipe
import dev.akkraizen.berill.plugins.finance.FinancePlugin
import dev.akkraizen.berill.plugins.finance.data.*
import dev.akkraizen.berill.plugins.languages.LanguagesPlugin
import dev.akkraizen.berill.plugins.languages.data.*
import dev.akkraizen.berill.plugins.health.HealthPlugin
import dev.akkraizen.berill.plugins.nutrition.NutritionPlugin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.*
import kotlin.test.*

class ConnectedFeaturesTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val core: BerillCore
  init { BerillDatabase.Schema.create(driver); core = BerillCore(driver, BerillPlugins, scope) }
  @AfterTest fun close() { scope.cancel(); driver.close() }

  @Test fun homeQuickActionsOfferPopupsWithoutCreatingRecordsOrNavigating() {
    val before = core.nav.current
    val plugins = listOf(FinancePlugin, NutritionPlugin, dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin, dev.akkraizen.berill.plugins.health.HealthPlugin)
    plugins.forEach { plugin ->
      val actions = plugin.quickActions(core.plugins.context(plugin))
      assertTrue(actions.isNotEmpty())
      assertTrue(actions.all { it.popup != null }, plugin.id)
    }
    assertEquals(before, core.nav.current)
    assertTrue(core.notes.all().isEmpty())
    assertTrue(core.data.raw(FinancePlugin.OPERATIONS).all().isEmpty())
    assertTrue(core.data.raw(NutritionPlugin.FOODS).all().isEmpty())
    assertTrue(core.data.raw(dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin.LOGS).all().isEmpty())
    assertTrue(core.data.raw(dev.akkraizen.berill.plugins.health.HealthPlugin.VITALS).all().isEmpty())
    assertTrue(core.data.raw(dev.akkraizen.berill.plugins.health.HealthPlugin.WELLBEING).all().isEmpty())
  }

  @Test fun closingPopupDoesNotPopUnderlyingNavigationAndRetainsPluginServices() {
    core.nav.openNote("underlying")
    val before = core.nav.current
    val ctx = core.plugins.context(NutritionPlugin)
    var closes = 0
    val popup = dev.akkraizen.berill.api.ui.layout.popupContext(ctx) { closes++ }
    assertSame(ctx.data, popup.data)
    assertSame(ctx.http, popup.http)
    assertTrue(popup.nav.back())
    assertEquals(1, closes)
    assertEquals(before, core.nav.current)
    assertEquals(ctx.pluginId, popup.pluginId)
  }

  @Test fun globalSearchFindsIndependentCollectionsAndOpensTheirRoutes() = runBlocking {
    core.notes.save("note", "Unique note", "")
    core.data.collection<Book>(BooksPlugin.BOOKS).save("book", Book(title = "Unique book"))
    core.data.collection<Recipe>(RecipesPlugin.RECIPES).save("recipe", Recipe(title = "Unique recipe"))
    core.data.collection<Operation>(FinancePlugin.OPERATIONS).save("expense", Operation(comment = "Unique transaction"))
    val results = core.connections.search("Unique")
    assertEquals(4, results.size)
    results.first { it.title == "Unique book" }.open()
    assertEquals(Destination.Plugin("books", "book:book"), core.nav.current)
    core.plugins.setEnabled("books", false)
    withTimeout(5000) { core.plugins.enabled.first { list -> list.none { it.id == "books" } } }
    assertEquals(3, core.connections.search("Unique").size)
    Unit
  }
  @Test fun recurringOperationsAreIdempotentAndRespectDeletedOccurrences() = runBlocking {
    val date = today()
    val rule = RecurringOperation(Operation(amount = 100.0, date = date.toString()), date.toString(), monthly = false, anchorDay = date.day)
    val rules = core.data.collection<RecurringOperation>(FinancePlugin.RECURRING)
    rules.save("rent", rule)
    val maintenance = FinancePlugin.provides(core.plugins.context(FinancePlugin)).filterIsInstance<MaintenanceSource>().single()
    maintenance.update(); maintenance.update()
    val ops = core.data.collection<Operation>(FinancePlugin.OPERATIONS)
    assertEquals(1, ops.all().size)
    val id = ops.all().single().id
    ops.delete(id)
    rules.save("rent", rule)
    maintenance.update()
    assertTrue(core.data.raw(FinancePlugin.OPERATIONS).isDeleted(id))
    assertTrue(ops.all().isEmpty())
  }
  @Test fun flashcardRatingsPersistAndRemoveReviewedWordsFromTodaysDeck() = runBlocking {
    core.data.collection<Word>(LanguagesPlugin.WORDS).save("word", Word(word = "Hallo", translation = "Hello"))
    val instance = WidgetInstance("languages.card", "de")
    core.widgets.perform(instance, WidgetAction.Run("known"))
    assertEquals(1, core.data.collection<WordReview>(LanguagesPlugin.REVIEWS).get("word")!!.intervalDays)
    assertTrue(core.widgets.content(instance).blocks.none { it is WidgetBlock.Card })
  }
  @Test fun healthImportsPreserveManualWeightAndAreVisibleViaCapabilities() {
    val source = NutritionPlugin.provides(core.plugins.context(NutritionPlugin)).filterIsInstance<HealthDataSink>().single()
    val date = today().toString()
    core.data.raw(NutritionPlugin.WEIGHTS).patch(date, mapOf("date" to kotlinx.serialization.json.JsonPrimitive(date), "kg" to kotlinx.serialization.json.JsonPrimitive(70.0)))
    source.accept(HealthDay(date, 8000, 450, 80.0))
    assertEquals("70.0", core.data.raw(NutritionPlugin.WEIGHTS).get(date)!!.fields["kg"].toString())
    HealthPlugin.provides(core.plugins.context(HealthPlugin)).filterIsInstance<HealthDataSink>().single().accept(HealthDay(date, 8000, 450, 80.0))
    val summaries = core.plugins.capabilities.all<HealthSummarySource>().mapNotNull { it.day(today()) }
    assertEquals(listOf(8000L), summaries.mapNotNull { it.steps })
    assertEquals(listOf(450L), summaries.mapNotNull { it.sleepMinutes })
    assertEquals(listOf(80.0), summaries.mapNotNull { it.weightKg })
  }
  @Test fun manuallyEditingImportedWeightStopsHealthConnectFromOverwritingIt() {
    val source = NutritionPlugin.provides(core.plugins.context(NutritionPlugin)).filterIsInstance<HealthDataSink>().single()
    val date = today().toString()
    source.accept(HealthDay(date, weightKg = 80.0))
    core.data.collection<dev.akkraizen.berill.plugins.nutrition.data.WeightEntry>(NutritionPlugin.WEIGHTS).save(date, dev.akkraizen.berill.plugins.nutrition.data.WeightEntry(date, 70.0))
    source.accept(HealthDay(date, weightKg = 85.0))
    assertEquals("70.0", core.data.raw(NutritionPlugin.WEIGHTS).get(date)!!.fields["kg"].toString())
  }
  @Test fun dayFiltersByDateAndIncludesImportedHealthData() = runBlocking {
    val date = today()
    core.data.collection<Operation>(FinancePlugin.OPERATIONS).save("today", Operation(amount = 10.0, date = date.toString(), comment = "Today expense"))
    core.data.collection<Operation>(FinancePlugin.OPERATIONS).save("old", Operation(date = date.minus(1, DateTimeUnit.DAY).toString(), comment = "Old expense"))
    val entries = core.connections.day(date)
    assertTrue(entries.any { it.id.endsWith("/today") })
    assertTrue(entries.none { it.id.endsWith("/old") })
  }
}
