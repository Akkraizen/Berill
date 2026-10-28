package dev.akkraizen.berill.api.widgets

import dev.akkraizen.berill.api.i18n.Tr
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class WidgetSize { Small, Medium, Large }

interface WidgetProvider {
  val id: String

  val title: Tr

  val description: Tr

  val size: WidgetSize
    get() = WidgetSize.Medium

  val choices: WidgetChoices?
    get() = null

  suspend fun content(request: WidgetRequest): WidgetContent

  suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect = WidgetEffect.Refresh()

  suspend fun summary(request: WidgetRequest): WidgetRow? = null
}

class WidgetChoices(val title: Tr, val required: Boolean = true, val list: suspend () -> List<WidgetChoice>)

@Serializable
data class WidgetChoice(val id: String, val label: String, val hint: String? = null)

@Serializable
data class WidgetRequest(
  val choice: String? = null,
  val state: Map<String, String> = emptyMap(),
  val nowMillis: Long = 0
)

@Serializable
sealed interface WidgetEffect {
  @Serializable
  @SerialName("refresh")
  data class Refresh(val state: Map<String, String>? = null, val refreshAtMillis: Long? = null) : WidgetEffect

  @Serializable
  @SerialName("openApp")
  data class OpenApp(val state: Map<String, String>? = null) : WidgetEffect
}

@Serializable
sealed interface WidgetAction {
  @Serializable
  @SerialName("open")
  data class Open(val route: String? = null, val page: String? = null, val pluginId: String? = null) : WidgetAction

  @Serializable
  @SerialName("note")
  data class OpenNote(val noteId: String? = null) : WidgetAction

  @Serializable
  @SerialName("home")
  data object OpenHome : WidgetAction

  @Serializable
  @SerialName("run")
  data class Run(val id: String, val args: Map<String, String> = emptyMap(), val opensApp: Boolean = false) : WidgetAction
}

enum class WidgetTone { Normal, Accent, Good, Warn, Bad, Muted }

enum class WidgetIcon { Add, Remove, Check, Play, Stop, Next, Previous, Refresh, Open, Note, Water, Timer, Food, Book, Movie, Fitness, Calendar, Money, Recipe, School, Translate, Habit, Mood }

@Serializable
data class WidgetContent(
  val title: String,
  val subtitle: String? = null,
  val icon: WidgetIcon? = null,
  val open: WidgetAction? = WidgetAction.OpenHome,
  val buttons: List<WidgetButton> = emptyList(),
  val blocks: List<WidgetBlock> = emptyList(),
  val empty: String? = null
)

@Serializable
data class WidgetButton(
  val label: String,
  val action: WidgetAction,
  val icon: WidgetIcon? = null,
  val primary: Boolean = false,
  val selected: Boolean = false
)

@Serializable
data class WidgetRow(
  val title: String,
  val subtitle: String? = null,
  val trailing: String? = null,
  val action: WidgetAction? = null,
  val tone: WidgetTone = WidgetTone.Normal,
  val icon: WidgetIcon? = null,
  val button: WidgetButton? = null
)

@Serializable
data class WidgetCheck(
  val label: String,
  val checked: Boolean,
  val action: WidgetAction,
  val subtitle: String? = null,
  val trailing: String? = null
)

@Serializable
data class WidgetMetric(
  val label: String,
  val value: String,
  val subtitle: String? = null,
  val progress: Float? = null,
  val tone: WidgetTone = WidgetTone.Normal
)

@Serializable
data class WidgetDay(
  val label: String,
  val inMonth: Boolean = true,
  val today: Boolean = false,
  val selected: Boolean = false,
  val events: Int = 0,
  val action: WidgetAction? = null
)

@Serializable
data class WidgetGridRow(val label: String, val cells: List<Float?>, val trailing: String? = null)

@Serializable
sealed interface WidgetBlock {
  @Serializable
  @SerialName("ring")
  data class Ring(val metric: WidgetMetric) : WidgetBlock

  @Serializable
  @SerialName("metrics")
  data class Metrics(val items: List<WidgetMetric>) : WidgetBlock

  @Serializable
  @SerialName("bar")
  data class Bar(val label: String, val value: String, val fraction: Float, val tone: WidgetTone = WidgetTone.Accent) : WidgetBlock

  @Serializable
  @SerialName("checks")
  data class Checks(val items: List<WidgetCheck>) : WidgetBlock

  @Serializable
  @SerialName("rows")
  data class Rows(val items: List<WidgetRow>) : WidgetBlock

  @Serializable
  @SerialName("buttons")
  data class Buttons(val items: List<WidgetButton>) : WidgetBlock

  @Serializable
  @SerialName("sparkline")
  data class Sparkline(val values: List<Double>, val start: String? = null, val end: String? = null) : WidgetBlock

  @Serializable
  @SerialName("grid")
  data class Grid(val header: List<String>, val rows: List<WidgetGridRow>) : WidgetBlock

  @Serializable
  @SerialName("month")
  data class Month(val weekdays: List<String>, val days: List<WidgetDay>) : WidgetBlock

  @Serializable
  @SerialName("countdown")
  data class Countdown(val endsAtMillis: Long?, val label: String, val idle: String) : WidgetBlock

  @Serializable
  @SerialName("card")
  data class Card(val front: String, val back: String?, val hint: String? = null) : WidgetBlock

  @Serializable
  @SerialName("text")
  data class Text(val text: String, val tone: WidgetTone = WidgetTone.Normal, val large: Boolean = false) : WidgetBlock
}

fun widgetFraction(value: Double, target: Double): Float =
  if (target <= 0.0) 0f else (value / target).toFloat().coerceIn(0f, 1f)
