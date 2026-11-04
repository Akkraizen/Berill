package dev.akkraizen.berill.plugins.health.data

import kotlin.math.abs
import kotlin.math.sqrt

enum class InsightFactor { SLEEP, STEPS }

enum class InsightMetric { WELLBEING, MOOD, ENERGY }

data class InsightDay(val sleepMinutes: Long?, val steps: Long?, val wellbeing: Int?, val mood: Int?, val energy: Int?) {
  fun factor(factor: InsightFactor): Double? = when (factor) {
    InsightFactor.SLEEP -> sleepMinutes?.toDouble()
    InsightFactor.STEPS -> steps?.toDouble()
  }

  fun metric(metric: InsightMetric): Double? = when (metric) {
    InsightMetric.WELLBEING -> wellbeing
    InsightMetric.MOOD -> mood
    InsightMetric.ENERGY -> energy
  }?.toDouble()
}

data class Insight(
  val factor: InsightFactor,
  val metric: InsightMetric,
  val threshold: Double,
  val above: Double,
  val below: Double,
  val aboveDays: Int,
  val belowDays: Int,
  val correlation: Double?
) {
  val difference: Double get() = above - below
}

const val MIN_GROUP_DAYS = 3
const val MIN_CORRELATION_DAYS = 7

fun insights(days: List<InsightDay>, sleepGoal: Int, stepsGoal: Int): List<Insight> =
  InsightFactor.entries.flatMap { factor ->
    val threshold = if (factor == InsightFactor.SLEEP) sleepGoal.toDouble() else stepsGoal.toDouble()
    InsightMetric.entries.mapNotNull { metric ->
      val pairs = days.mapNotNull { day -> day.factor(factor)?.let { f -> day.metric(metric)?.let { m -> f to m } } }
      val high = pairs.filter { it.first >= threshold }.map { it.second }
      val low = pairs.filter { it.first < threshold }.map { it.second }
      if (high.size < MIN_GROUP_DAYS || low.size < MIN_GROUP_DAYS) return@mapNotNull null
      Insight(factor, metric, threshold, high.average(), low.average(), high.size, low.size, pearson(pairs).takeIf { pairs.size >= MIN_CORRELATION_DAYS })
    }
  }.sortedByDescending { abs(it.difference) }

fun pearson(pairs: List<Pair<Double, Double>>): Double? {
  if (pairs.size < 2) return null
  val mx = pairs.map { it.first }.average()
  val my = pairs.map { it.second }.average()
  var sxy = 0.0
  var sxx = 0.0
  var syy = 0.0
  for ((x, y) in pairs) {
    sxy += (x - mx) * (y - my)
    sxx += (x - mx) * (x - mx)
    syy += (y - my) * (y - my)
  }
  if (sxx == 0.0 || syy == 0.0) return null
  return sxy / sqrt(sxx * syy)
}

enum class Strength { NONE, WEAK, MODERATE, STRONG }

fun strength(r: Double?): Strength = when {
  r == null -> Strength.NONE
  abs(r) >= 0.6 -> Strength.STRONG
  abs(r) >= 0.35 -> Strength.MODERATE
  abs(r) >= 0.15 -> Strength.WEAK
  else -> Strength.NONE
}
