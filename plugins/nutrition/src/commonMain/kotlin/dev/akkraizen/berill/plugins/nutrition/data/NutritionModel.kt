package dev.akkraizen.berill.plugins.nutrition.data

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.plugins.nutrition.NutritionPlugin
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FoodEntry(
  val date: String,
  val meal: String = MEAL_BREAKFAST,
  val name: String,
  val brand: String? = null,
  val grams: Double = 100.0,
  val kcal100: Double = 0.0,
  val protein100: Double = 0.0,
  val fat100: Double = 0.0,
  val carbs100: Double = 0.0,
  val code: String? = null,
  val createdAt: Long = 0
) {
  val kcal: Double
    get() = kcal100 * grams / 100

  val protein: Double
    get() = protein100 * grams / 100

  val fat: Double
    get() = fat100 * grams / 100

  val carbs: Double
    get() = carbs100 * grams / 100
}

@Serializable
data class WeightEntry(val date: String, val kg: Double)

@Serializable
data class WaterDay(val date: String, val ml: Int)

data class Product(
  val name: String,
  val brand: String? = null,
  val kcal100: Double,
  val protein100: Double = 0.0,
  val fat100: Double = 0.0,
  val carbs100: Double = 0.0,
  val code: String? = null,
  val image: String? = null,
  val servingGrams: Double? = null,
  val defaultGrams: Double = 100.0
) {
  fun toEntry(date: String, meal: String, grams: Double, createdAt: Long) = FoodEntry(
    date = date,
    meal = meal,
    name = name,
    brand = brand,
    grams = grams,
    kcal100 = kcal100,
    protein100 = protein100,
    fat100 = fat100,
    carbs100 = carbs100,
    code = code,
    createdAt = createdAt
  )
}

fun FoodEntry.toProduct() = Product(name, brand, kcal100, protein100, fat100, carbs100, code, defaultGrams = grams)

const val MEAL_BREAKFAST = "breakfast"
const val MEAL_LUNCH = "lunch"
const val MEAL_DINNER = "dinner"
const val MEAL_SNACK = "snack"
val MEALS = listOf(MEAL_BREAKFAST, MEAL_LUNCH, MEAL_DINNER, MEAL_SNACK)

fun mealByHour(hour: Int): String =
  when (hour) {
    in 4..10 -> MEAL_BREAKFAST
    in 11..15 -> MEAL_LUNCH
    in 18..22 -> MEAL_DINNER
    else -> MEAL_SNACK
  }

const val SEX_MALE = "male"
const val SEX_FEMALE = "female"
const val GOAL_LOSE = "lose"
const val GOAL_MAINTAIN = "maintain"
const val GOAL_GAIN = "gain"

val ACTIVITY_LEVELS = listOf(1.2, 1.375, 1.55, 1.725, 1.9)
val RATES = listOf(0.25, 0.5, 0.75, 1.0)

@Serializable
data class Profile(
  val sex: String = SEX_MALE,
  val age: Int = 30,
  val heightCm: Double = 175.0,
  val weightKg: Double = 75.0,
  val activity: Double = 1.375,
  val goal: String = GOAL_MAINTAIN,
  val rateKg: Double = 0.5
)

@Serializable
data class Targets(
  val kcal: Int? = null,
  val protein: Int? = null,
  val fat: Int? = null,
  val carbs: Int? = null,
  val waterMl: Int? = null
)

data class Norm(
  val bmr: Int,
  val tdee: Int,
  val kcal: Int,
  val protein: Int,
  val fat: Int,
  val carbs: Int,
  val waterMl: Int
)

private const val KCAL_PER_KG_WEEK = 1100.0

fun computeNorm(p: Profile, weightKg: Double): Norm {
  val bmr = 10 * weightKg + 6.25 * p.heightCm - 5 * p.age + if (p.sex == SEX_FEMALE) -161 else 5
  val tdee = bmr * p.activity
  val delta = when (p.goal) {
    GOAL_LOSE -> -KCAL_PER_KG_WEEK * p.rateKg
    GOAL_GAIN -> KCAL_PER_KG_WEEK * p.rateKg
    else -> 0.0
  }
  val floor = if (p.sex == SEX_FEMALE) 1200.0 else 1500.0
  val kcal = roundTo10((tdee + delta).coerceAtLeast(minOf(floor, tdee)))
  val protein = (weightKg * if (p.goal == GOAL_LOSE) 1.8 else 1.6).roundToInt()
  val fat = (weightKg * 0.9).roundToInt()
  val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
  val water = ((weightKg * 30) / 250).roundToInt().coerceAtLeast(6) * 250
  return Norm(bmr.roundToInt(), tdee.roundToInt(), kcal, protein, fat, carbs, water)
}

fun Norm.withOverrides(t: Targets) =
  copy(
    kcal = t.kcal ?: kcal,
    protein = t.protein ?: protein,
    fat = t.fat ?: fat,
    carbs = t.carbs ?: carbs,
    waterMl = t.waterMl ?: waterMl
  )

private fun roundTo10(v: Double): Int = (v / 10).roundToInt() * 10

fun latestWeight(
  weights: List<WeightEntry>,
  profile: Profile
): Double = weights.maxByOrNull { it.date }?.kg ?: profile.weightKg

data class Totals(val kcal: Double = 0.0, val protein: Double = 0.0, val fat: Double = 0.0, val carbs: Double = 0.0) {
  operator fun plus(e: FoodEntry) = Totals(kcal + e.kcal, protein + e.protein, fat + e.fat, carbs + e.carbs)
}

fun List<FoodEntry>.totals(): Totals = fold(Totals()) { acc, e -> acc + e }

fun formatNum(value: Double, decimals: Int = 1): String {
  var f = 1.0
  repeat(decimals) { f *= 10 }
  val r = kotlin.math.round(value * f) / f
  return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}

fun Double.kcalText(): String = roundToInt().toString()

internal fun foods(ctx: PluginContext) = ctx.data.collection(NutritionPlugin.FOODS, FoodEntry.serializer())

internal fun weights(ctx: PluginContext) = ctx.data.collection(NutritionPlugin.WEIGHTS, WeightEntry.serializer())

internal fun water(ctx: PluginContext) = ctx.data.collection(NutritionPlugin.WATER, WaterDay.serializer())

internal val NutritionJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

internal fun LocalDate.iso(): String = toString()
