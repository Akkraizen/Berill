package dev.akkraizen.berill.api.ui

import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.feedback.Tone
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

val MONTHS_NOM: List<String>
  get() = I18n.list("dates.months.nom")
val MONTHS_GEN: List<String>
  get() = I18n.list("dates.months.gen")
val MONTHS_SHORT: List<String>
  get() = I18n.list("dates.months.short")
val DAYS_SHORT: List<String>
  get() = I18n.list("dates.days.short")
val DAYS_FULL: List<String>
  get() = I18n.list("dates.days.full")

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun String?.toDateOrNull(): LocalDate? =
  this?.trim()?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun LocalDate.dayMonth(): String =
  I18n.text("dates.dayMonth", "day" to day, "month" to MONTHS_GEN[month.ordinal])

fun LocalDate.dayMonthShort(): String =
  I18n.text("dates.dayMonthShort", "day" to day, "month" to MONTHS_SHORT[month.ordinal])

fun LocalDate.ddMM(): String = "${day.twoDigits()}.${(month.ordinal + 1).twoDigits()}"

fun LocalDate.ddMMyyyy(): String = "${ddMM()}.$year"

fun localDateTime(millis: Long): LocalDateTime =
  Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())

fun LocalDateTime.hhmm(): String = "${hour.twoDigits()}:${minute.twoDigits()}"

fun LocalDateTime.hhmmss(): String = "${hhmm()}:${second.twoDigits()}"

private fun Int.twoDigits(): String = toString().padStart(2, '0')

val LocalDate.weekdayIndex: Int
  get() = dayOfWeek.isoDayNumber - 1

fun LocalDate.mondayOfWeek(): LocalDate = minus(DatePeriod(days = weekdayIndex))

fun LocalDate.plusDays(n: Int): LocalDate = plus(DatePeriod(days = n))

fun LocalDate.minusDays(n: Int): LocalDate = minus(DatePeriod(days = n))

fun LocalDate.daysFromToday(): Int = today().daysUntil(this)

fun LocalDate.isoWeek(): Pair<Int, Int> {
  val thursday = mondayOfWeek().plusDays(3)
  val firstDay = LocalDate(thursday.year, 1, 1)
  val week = firstDay.daysUntil(thursday) / 7 + 1
  return thursday.year to week
}

fun LocalDate.isoWeekId(): String {
  val (year, week) = isoWeek()
  return "$year-W${week.twoDigits()}"
}

fun mondayOfIsoWeek(weekId: String): LocalDate? {
  val m = Regex("""(\d{4})-W(\d{2})""").matchEntire(weekId) ?: return null
  val (year, week) = m.destructured
  val jan4 = LocalDate(year.toInt(), 1, 4)
  return jan4.mondayOfWeek().plusDays((week.toInt() - 1) * 7)
}

data class DeadlineInfo(val label: String, val tone: Tone, val days: Int)

fun deadlineInfo(date: LocalDate?): DeadlineInfo {
  if (date == null) return DeadlineInfo(UiText.noDeadline(), Tone.Muted, Int.MAX_VALUE)
  val days = date.daysFromToday()
  return when {
    days < 0 -> DeadlineInfo(UiText.overdueBy(-days)(), Tone.Bad, days)
    days == 0 -> DeadlineInfo(UiText.deadlineToday(), Tone.Bad, days)
    days == 1 -> DeadlineInfo(UiText.deadlineTomorrow(), Tone.Warn, days)
    days <= 7 -> DeadlineInfo(UiText.inDays(days)(), Tone.Warn, days)
    else -> DeadlineInfo(date.dayMonth(), Tone.Muted, days)
  }
}

fun formatMinutes(total: Int): String =
  when {
    total < 60 -> UiText.minutes(total)()
    total % 60 == 0 -> UiText.hours(total / 60)()
    else -> UiText.hoursMinutes(total / 60, total % 60)()
  }

data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
  operator fun plus(months: Int): YearMonth {
    val index = year * 12 + (month - 1) + months
    return YearMonth(index.floorDiv(12), index.mod(12) + 1)
  }

  operator fun contains(date: LocalDate): Boolean =
    date.year == year && date.month.ordinal + 1 == month

  override fun compareTo(other: YearMonth) = compareValuesBy(this, other, { it.year }, { it.month })

  val first: LocalDate
    get() = LocalDate(year, month, 1)

  val lengthDays: Int
    get() = first.daysUntil(plus(1).first)

  val title: String
    get() = "${MONTHS_NOM[month - 1]} $year"

  companion object {
    fun of(date: LocalDate) = YearMonth(date.year, date.month.ordinal + 1)

    fun current() = of(today())
  }
}
