package dev.akkraizen.berill.plugins.nutrition.i18n

import dev.akkraizen.berill.plugins.nutrition.data.GOAL_GAIN
import dev.akkraizen.berill.plugins.nutrition.data.GOAL_LOSE
import dev.akkraizen.berill.plugins.nutrition.data.MEAL_BREAKFAST
import dev.akkraizen.berill.plugins.nutrition.data.MEAL_DINNER
import dev.akkraizen.berill.plugins.nutrition.data.MEAL_LUNCH
import dev.akkraizen.berill.plugins.nutrition.data.formatNum

internal object NutritionText {

  val title = pluginI18n.tr("nutrition.title")
  val description = pluginI18n.tr("nutrition.description")
  val pageDiary = pluginI18n.tr("nutrition.pageDiary")
  val pageWeight = pluginI18n.tr("nutrition.pageWeight")
  val pageStats = pluginI18n.tr("nutrition.pageStats")
  val pageNorm = pluginI18n.tr("nutrition.pageNorm")
  val homeCard = pluginI18n.tr("nutrition.homeCard")
  val quickFood = pluginI18n.tr("nutrition.quickFood")

  val kcal = pluginI18n.tr("nutrition.kcal")

  fun kcalValue(v: String) = pluginI18n.tr("nutrition.kcalValue", "v" to v)

  fun gramsValue(v: String) = pluginI18n.tr("nutrition.gramsValue", "v" to v)

  fun kgValue(v: String) = pluginI18n.tr("nutrition.kgValue", "v" to v)

  fun mlValue(v: String) = pluginI18n.tr("nutrition.mlValue", "v" to v)

  fun litersValue(v: String) = pluginI18n.tr("nutrition.litersValue", "v" to v)

  val protein = pluginI18n.tr("nutrition.protein")
  val fat = pluginI18n.tr("nutrition.fat")
  val carbs = pluginI18n.tr("nutrition.carbs")
  val proteinShort = pluginI18n.tr("nutrition.proteinShort")
  val fatShort = pluginI18n.tr("nutrition.fatShort")
  val carbsShort = pluginI18n.tr("nutrition.carbsShort")
  val add = pluginI18n.tr("nutrition.add")
  val save = pluginI18n.tr("nutrition.save")
  val cancel = pluginI18n.tr("nutrition.cancel")
  val delete = pluginI18n.tr("nutrition.delete")
  val today = pluginI18n.tr("nutrition.today")
  val yesterday = pluginI18n.tr("nutrition.yesterday")
  val previousDay = pluginI18n.tr("nutrition.previousDay")
  val nextDay = pluginI18n.tr("nutrition.nextDay")

  fun days(n: Int) =
    pluginI18n.plural("nutrition.days", n)

  fun entries(n: Int) =
    pluginI18n.plural("nutrition.entries", n)

  fun mealLabel(meal: String) =
    when (meal) {
      MEAL_BREAKFAST -> pluginI18n.tr("nutrition.mealLabel")
      MEAL_LUNCH -> pluginI18n.tr("nutrition.mealLabel.2")
      MEAL_DINNER -> pluginI18n.tr("nutrition.mealLabel.3")
      else -> pluginI18n.tr("nutrition.mealLabel.4")
    }()

  val eaten = pluginI18n.tr("nutrition.eaten")
  val left = pluginI18n.tr("nutrition.left")
  val over = pluginI18n.tr("nutrition.over")

  fun ofTarget(eaten: String, target: String) = pluginI18n.tr("nutrition.ofTarget", "eaten" to eaten, "target" to target)

  val noFood = pluginI18n.tr("nutrition.noFood")

  fun addTo(meal: String) = pluginI18n.tr("nutrition.addTo", "meal" to meal)

  val portion = pluginI18n.tr("nutrition.portion")
  val grams = pluginI18n.tr("nutrition.grams")
  val per100 = pluginI18n.tr("nutrition.per100")

  fun serving(g: String) = pluginI18n.tr("nutrition.serving", "g" to g)

  val meal = pluginI18n.tr("nutrition.meal")

  fun deleteEntry(name: String) = pluginI18n.tr("nutrition.deleteEntry", "name" to name)

  fun addTitle(meal: String, day: String) = pluginI18n.tr("nutrition.addTitle", "meal" to meal, "day" to day)

  val searchTitle = pluginI18n.tr("nutrition.searchTitle")
  val searchPlaceholder = pluginI18n.tr("nutrition.searchPlaceholder")
  val barcodeTitle = pluginI18n.tr("nutrition.barcodeTitle")
  val barcode = pluginI18n.tr("nutrition.barcode")
  val barcodeNotFound = pluginI18n.tr("nutrition.barcodeNotFound")
  val manualTitle = pluginI18n.tr("nutrition.manualTitle")
  val name = pluginI18n.tr("nutrition.name")
  val kcalPer100 = pluginI18n.tr("nutrition.kcalPer100")
  val proteinPer100 = pluginI18n.tr("nutrition.proteinPer100")
  val fatPer100 = pluginI18n.tr("nutrition.fatPer100")
  val carbsPer100 = pluginI18n.tr("nutrition.carbsPer100")
  val recentTitle = pluginI18n.tr("nutrition.recentTitle")
  val noRecent = pluginI18n.tr("nutrition.noRecent")

  fun added(name: String) = pluginI18n.tr("nutrition.added", "name" to name)

  val searchHint = pluginI18n.tr("nutrition.searchHint")

  val water = pluginI18n.tr("nutrition.water")

  fun glasses(n: Int) =
    pluginI18n.plural("nutrition.glasses", n)

  val addGlass = pluginI18n.tr("nutrition.addGlass")
  val removeGlass = pluginI18n.tr("nutrition.removeGlass")

  val weighIn = pluginI18n.tr("nutrition.weighIn")
  val weightKg = pluginI18n.tr("nutrition.weightKg")
  val date = pluginI18n.tr("nutrition.date")
  val current = pluginI18n.tr("nutrition.current")
  val change30 = pluginI18n.tr("nutrition.change30")
  val sinceStart = pluginI18n.tr("nutrition.sinceStart")
  val min = pluginI18n.tr("nutrition.min")
  val chart = pluginI18n.tr("nutrition.chart")
  val history = pluginI18n.tr("nutrition.history")
  val noWeights = pluginI18n.tr("nutrition.noWeights")
  val needTwoPoints = pluginI18n.tr("nutrition.needTwoPoints")
  val range30 = pluginI18n.tr("nutrition.range30")
  val range90 = pluginI18n.tr("nutrition.range90")
  val range365 = pluginI18n.tr("nutrition.range365")
  val rangeAll = pluginI18n.tr("nutrition.rangeAll")
  val range7 = pluginI18n.tr("nutrition.range7")

  fun weightDelta(v: String) = pluginI18n.tr("nutrition.weightDelta", "v" to v)

  val avgKcal = pluginI18n.tr("nutrition.avgKcal")
  val perDay = pluginI18n.tr("nutrition.perDay")
  val daysLogged = pluginI18n.tr("nutrition.daysLogged")
  val inTarget = pluginI18n.tr("nutrition.inTarget")
  val inTargetHint = pluginI18n.tr("nutrition.inTargetHint")
  val avgWater = pluginI18n.tr("nutrition.avgWater")
  val kcalByDay = pluginI18n.tr("nutrition.kcalByDay")
  val target = pluginI18n.tr("nutrition.target")
  val macrosAvg = pluginI18n.tr("nutrition.macrosAvg")

  fun energyShare(p: Int) = pluginI18n.tr("nutrition.energyShare", "p" to p)

  val noStats = pluginI18n.tr("nutrition.noStats")
  val topFoods = pluginI18n.tr("nutrition.topFoods")

  val profile = pluginI18n.tr("nutrition.profile")
  val sex = pluginI18n.tr("nutrition.sex")
  val male = pluginI18n.tr("nutrition.male")
  val female = pluginI18n.tr("nutrition.female")
  val age = pluginI18n.tr("nutrition.age")
  val height = pluginI18n.tr("nutrition.height")
  val weightFromLog = pluginI18n.tr("nutrition.weightFromLog")

  fun weightFromLogValue(v: String) = pluginI18n.tr("nutrition.weightFromLogValue", "v" to v)

  val activity = pluginI18n.tr("nutrition.activity")

  fun activityLabel(a: Double) =
    when (a) {
      1.2 -> pluginI18n.tr("nutrition.activityLabel")
      1.375 -> pluginI18n.tr("nutrition.activityLabel.2")
      1.55 -> pluginI18n.tr("nutrition.activityLabel.3")
      1.725 -> pluginI18n.tr("nutrition.activityLabel.4")
      else -> pluginI18n.tr("nutrition.activityLabel.5")
    }()

  val goal = pluginI18n.tr("nutrition.goal")

  fun goalLabel(g: String) =
    when (g) {
      GOAL_LOSE -> pluginI18n.tr("nutrition.goalLabel")
      GOAL_GAIN -> pluginI18n.tr("nutrition.goalLabel.2")
      else -> pluginI18n.tr("nutrition.goalLabel.3")
    }()

  val rate = pluginI18n.tr("nutrition.rate")

  fun rateLabel(r: Double) = pluginI18n.tr("nutrition.rateLabel", "p0" to formatNum(r, 2))()

  val calculation = pluginI18n.tr("nutrition.calculation")
  val bmr = pluginI18n.tr("nutrition.bmr")
  val bmrHint = pluginI18n.tr("nutrition.bmrHint")
  val tdee = pluginI18n.tr("nutrition.tdee")
  val tdeeHint = pluginI18n.tr("nutrition.tdeeHint")
  val dailyTarget = pluginI18n.tr("nutrition.dailyTarget")
  val formulaHint = pluginI18n.tr("nutrition.formulaHint")
  val overrides = pluginI18n.tr("nutrition.overrides")
  val overridesHint =
    pluginI18n.tr("nutrition.overridesHint")

  fun auto(label: String, v: String) = pluginI18n.tr("nutrition.auto", "label" to label, "v" to v)

  val kcalField = pluginI18n.tr("nutrition.kcalField")
  val proteinField = pluginI18n.tr("nutrition.proteinField")
  val fatField = pluginI18n.tr("nutrition.fatField")
  val carbsField = pluginI18n.tr("nutrition.carbsField")
  val waterField = pluginI18n.tr("nutrition.waterField")
  val glassSize = pluginI18n.tr("nutrition.glassSize")
  val resetOverrides = pluginI18n.tr("nutrition.resetOverrides")
  val minimumNote = pluginI18n.tr("nutrition.minimumNote")

  val widgetCaloriesTitle = pluginI18n.tr("nutrition.widget.caloriesTitle")
  val widgetCaloriesDescription = pluginI18n.tr("nutrition.widget.caloriesDescription")
  val widgetWaterTitle = pluginI18n.tr("nutrition.widget.waterTitle")
  val widgetWaterDescription = pluginI18n.tr("nutrition.widget.waterDescription")
  val widgetWeightTitle = pluginI18n.tr("nutrition.widget.weightTitle")
  val widgetWeightDescription = pluginI18n.tr("nutrition.widget.weightDescription")
  val widgetNoWeight = pluginI18n.tr("nutrition.widget.noWeight")

  fun widgetWeightDelta(delta: String, n: Int) = pluginI18n.tr("nutrition.widget.weightDelta", "delta" to delta, "n" to n)
}
