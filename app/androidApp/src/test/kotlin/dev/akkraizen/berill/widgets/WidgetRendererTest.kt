package dev.akkraizen.berill.widgets

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.isNotChecked
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetButton
import dev.akkraizen.berill.api.widgets.WidgetCheck
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetDay
import dev.akkraizen.berill.api.widgets.WidgetGridRow
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetMetric
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.core.widgets.WidgetHub
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetRendererTest {
  private val context: Application = ApplicationProvider.getApplicationContext()

  private val sample = WidgetContent(
    title = "Сегодня",
    subtitle = "28 октября",
    icon = WidgetIcon.Calendar,
    open = WidgetAction.OpenHome,
    buttons = listOf(WidgetButton("Добавить", WidgetAction.Open(route = "add:"), WidgetIcon.Add)),
    blocks = listOf(
      WidgetBlock.Checks(listOf(WidgetCheck("Чтение", false, WidgetAction.Run("toggle", mapOf("habit" to "read")), subtitle = "норма: 30 мин"))),
      WidgetBlock.Rows(listOf(WidgetRow("Созвон", "Офис", "10:00", WidgetAction.Open(route = "event"), WidgetTone.Accent, WidgetIcon.Calendar, WidgetButton("+1", WidgetAction.Run("plus"))))),
      WidgetBlock.Buttons(listOf(WidgetButton("1:00", WidgetAction.Run("start")), WidgetButton("Начать", WidgetAction.Run("go", opensApp = true), WidgetIcon.Play, primary = true))),
      WidgetBlock.Bar("Белки", "40/120", 0.33f, WidgetTone.Good),
      WidgetBlock.Metrics(listOf(WidgetMetric("Свободно", "700 ₽"), WidgetMetric("Расходы", "300 ₽", tone = WidgetTone.Bad))),
      WidgetBlock.Ring(WidgetMetric("ккал", "1200", "осталось: 800", 0.6f)),
      WidgetBlock.Sparkline(listOf(80.0, 79.5, 79.2), "01.10", "28.10"),
      WidgetBlock.Grid(listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс"), listOf(WidgetGridRow("Бег", listOf(0f, 1f, 0.5f, null, 1f, 0f, 1f), "3 дн."))),
      WidgetBlock.Month(listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс"), (1..35).map { WidgetDay((it % 31 + 1).toString(), inMonth = it <= 31, today = it == 28, selected = it == 29, events = it % 4, action = WidgetAction.Run("select", mapOf("day" to it.toString()))) }),
      WidgetBlock.Countdown(System.currentTimeMillis() + 90_000, "Отдых", "Выберите время"),
      WidgetBlock.Card("apple", "яблоко", "[ˈæp.əl]"),
      WidgetBlock.Text("Как прошёл день?", WidgetTone.Muted)
    )
  )

  @Test
  fun everyBlockRenders() =
    runGlanceAppWidgetUnitTest {
      setContext(context)
      setAppWidgetSize(DpSize(320.dp, 640.dp))
      provideComposable { GlanceTheme { WidgetFrame(sample) } }
      listOf("Сегодня", "28 октября", "Чтение", "Созвон", "10:00", "+1", "1:00", "Начать", "Белки", "700 ₽", "1200", "60%", "01.10", "Бег", "3 дн.", "Отдых", "apple", "яблоко", "Как прошёл день?")
        .forEach { onNode(hasText(it)).assertExists() }
    }

  @Test
  fun runActionsUseCallbackAndOpeningActionsStartTheApp() =
    runGlanceAppWidgetUnitTest {
      setContext(context)
      setAppWidgetSize(DpSize(320.dp, 640.dp))
      provideComposable { GlanceTheme { WidgetFrame(sample) } }
      onNode(hasRunCallbackClickAction<WidgetActionCallback>(callbackParameters(WidgetAction.Run("start")))).assertExists()
      onNode(isNotChecked()).assertExists()
      onNode(hasRunCallbackClickAction<WidgetActionCallback>(callbackParameters(WidgetAction.Run("select", mapOf("day" to "28"))))).assertExists()
      onAllNodes(hasRunCallbackClickAction<WidgetActionCallback>(callbackParameters(WidgetAction.Run("go", opensApp = true)))).assertCountEquals(0)
    }

  private fun callbackParameters(action: WidgetAction) =
    actionParametersOf(WidgetActionCallback.ACTION to Json { encodeDefaults = false }.encodeToString(WidgetAction.serializer(), action))

  @Test
  fun emptyContentShowsHint() =
    runGlanceAppWidgetUnitTest {
      setContext(context)
      setAppWidgetSize(DpSize(180.dp, 110.dp))
      provideComposable { GlanceTheme { WidgetFrame(WidgetContent(title = "Привычки", empty = "Привычек пока нет")) } }
      onNode(hasText("Привычек пока нет")).assertExists()
    }

  @Test
  fun contentSurvivesTheStateRoundTrip() {
    val decoded = WidgetHub.decodeContent(Json.encodeToString(WidgetContent.serializer(), sample))
    assertEquals(sample, decoded)
  }

  @Test
  fun everyCatalogReceiverIsDeclaredWithProviderInfo() {
    val keys = WidgetCatalog.specs.mapNotNull { it.key }
    assertEquals(keys.size, keys.toSet().size)
    WidgetCatalog.specs.forEach { spec ->
      val info = context.packageManager.getReceiverInfo(ComponentName(context, spec.receiver), PackageManager.GET_META_DATA)
      assertTrue(info.exported, spec.receiver.name)
      assertNotNull(info.metaData?.getInt("android.appwidget.provider")?.takeIf { it != 0 }, spec.receiver.name)
      assertEquals(spec, WidgetCatalog.spec(spec.receiver.name))
    }
  }
}
