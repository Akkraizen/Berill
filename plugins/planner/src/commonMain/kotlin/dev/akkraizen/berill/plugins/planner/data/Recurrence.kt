package dev.akkraizen.berill.plugins.planner.data

import dev.akkraizen.berill.api.ui.mondayOfWeek
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.weekdayIndex
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable

const val DAILY = "daily"
const val WEEKLY = "weekly"
const val MONTHLY = "monthly"
const val YEARLY = "yearly"
const val BIRTHDAY = "birthday"

@Serializable
data class Repeat(
  val freq: String = WEEKLY,
  val interval: Int = 1,
  val days: List<Int> = emptyList(),
  val until: String? = null
) {
  val untilDate: LocalDate?
    get() = until.toDateOrNull()

  fun weekdays(first: LocalDate): List<Int> = days.filter { it in 1..7 }.distinct().sorted().ifEmpty { listOf(first.weekdayIndex + 1) }

  fun matches(first: LocalDate, day: LocalDate): Boolean {
    if (day < first) return false
    untilDate?.let { if (day > it) return false }
    val n = interval.coerceAtLeast(1)
    return when (freq) {
      DAILY -> first.daysUntil(day) % n == 0
      WEEKLY -> {
        val weeks = first.mondayOfWeek().daysUntil(day.mondayOfWeek()) / 7
        weeks % n == 0 && (day.weekdayIndex + 1) in weekdays(first)
      }
      MONTHLY -> {
        val months = (day.year - first.year) * 12 + (day.month.ordinal - first.month.ordinal)
        months % n == 0 && day.day == first.day
      }
      YEARLY -> {
        val years = day.year - first.year
        years % n == 0 && day.month == first.month &&
          (day.day == first.day || (first.month == Month.FEBRUARY && first.day == 29 && day.day == 28 && !isLeap(day.year)))
      }
      else -> day == first
    }
  }
}

fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
