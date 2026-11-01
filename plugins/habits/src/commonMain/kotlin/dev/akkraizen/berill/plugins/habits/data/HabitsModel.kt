package dev.akkraizen.berill.plugins.habits.data

import dev.akkraizen.berill.api.DataAccess
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.RawRecord
import dev.akkraizen.berill.api.ui.formatMinutes
import dev.akkraizen.berill.plugins.habits.HabitsPlugin
import dev.akkraizen.berill.plugins.habits.i18n.HabitsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class HabitDef(
  val label: String = "",
  val emoji: String? = null,
  val icon: String? = null,
  val defaultDuration: Int = 1,
  val unit: String = MINUTES,
  val monthlyGoal: Int? = null,
  val order: Int = 0,
  val archived: Boolean = false
)

const val MINUTES = "minutes"
const val PROBLEMS = "problems"
const val TIMES = "times"
val UNITS = listOf(MINUTES, PROBLEMS, TIMES)

fun unitTitle(unit: String) =
  when (unit) {
    MINUTES -> HabitsText.unitMinutes()
    PROBLEMS -> HabitsText.unitProblems()
    else -> HabitsText.unitTimes()
  }

fun formatAmount(unit: String, value: Int): String =
  when (unit) {
    MINUTES -> formatMinutes(value)
    PROBLEMS -> HabitsText.problems(value)
    else -> HabitsText.times(value)
  }

class HabitsData(val ctx: PluginContext) {
  private val data: DataAccess
    get() = ctx.data

  val defs = data.collection(HabitsPlugin.HABITS, HabitDef.serializer())
  val days = data.raw(HabitsPlugin.HABIT_DAYS)

  fun toggle(
    habitId: String,
    def: HabitDef,
    date: LocalDate,
    current: Int
  ) = setValue(habitId, date, if (current >= def.defaultDuration) 0 else def.defaultDuration)

  fun setValue(habitId: String, date: LocalDate, value: Int) {
    days.patch(date.toString(), mapOf(habitId to JsonPrimitive(value.coerceAtLeast(0))))
  }

  fun perfectGoal(
    key: String,
    default: Int
  ): Int = ctx.settings.get(key)?.jsonPrimitive?.doubleOrNull?.toInt() ?: default

  fun observePerfectGoal(
    key: String,
    default: Int
  ): Flow<Int> = ctx.settings.observe(key).map { (it as? JsonPrimitive)?.doubleOrNull?.toInt() ?: default }

  fun setPerfectGoal(key: String, value: Int) = ctx.settings.set(key, JsonPrimitive(value))
}

const val GOAL_MONTH = "perfectDaysMonthly"
const val GOAL_YEAR = "perfectDaysYearly"

enum class HabitIconKey { PUZZLE, CODE, FIRE, TRANSLATE, FITNESS, BOOK, TASK;
  val key: String get() = name.lowercase()
}
fun guessIconKey(id: String): String {
  val normalized = id.lowercase()
  return HabitIconKey.entries.firstOrNull { key -> key != HabitIconKey.TASK &&
    dev.akkraizen.berill.plugins.habits.i18n.pluginI18n.list("lexicon.habitIcon.${key.name}").any { it in normalized }
  }?.key ?: HabitIconKey.TASK.key
}

internal fun dayValue(fields: Map<String, JsonElement>?, habitId: String): Int =
  fields?.get(habitId)?.let { runCatching { it.jsonPrimitive.doubleOrNull?.toInt() }.getOrNull() }
    ?: 0

internal fun List<Item<HabitDef>>.active(): List<Item<HabitDef>> = filter { !it.value.archived }.sortedBy { it.value.order }

internal fun RawRecord?.valueOf(habitId: String) = dayValue(this?.fields, habitId)
