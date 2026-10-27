package dev.akkraizen.berill.plugins.workouts.i18n

import dev.akkraizen.berill.plugins.workouts.data.EQ_BARBELL
import dev.akkraizen.berill.plugins.workouts.data.EQ_BODYWEIGHT
import dev.akkraizen.berill.plugins.workouts.data.EQ_CABLE
import dev.akkraizen.berill.plugins.workouts.data.EQ_DUMBBELL
import dev.akkraizen.berill.plugins.workouts.data.EQ_KETTLEBELL
import dev.akkraizen.berill.plugins.workouts.data.EQ_MACHINE
import dev.akkraizen.berill.plugins.workouts.data.MUSCLE_BACK
import dev.akkraizen.berill.plugins.workouts.data.MUSCLE_BICEPS
import dev.akkraizen.berill.plugins.workouts.data.MUSCLE_CALVES
import dev.akkraizen.berill.plugins.workouts.data.MUSCLE_CHEST
import dev.akkraizen.berill.plugins.workouts.data.MUSCLE_CORE
import dev.akkraizen.berill.plugins.workouts.data.MUSCLE_GLUTES
import dev.akkraizen.berill.plugins.workouts.data.MUSCLE_LEGS
import dev.akkraizen.berill.plugins.workouts.data.MUSCLE_SHOULDERS
import dev.akkraizen.berill.plugins.workouts.data.MUSCLE_TRICEPS

internal object CatalogText {
  val page = pluginI18n.tr("catalog.page")
  val searchPlaceholder = pluginI18n.tr("catalog.searchPlaceholder")
  val allMuscles = pluginI18n.tr("catalog.allMuscles")
  val anyEquipment = pluginI18n.tr("catalog.anyEquipment")
  val muscle = pluginI18n.tr("catalog.muscle")
  val equipment = pluginI18n.tr("catalog.equipment")
  val newExercise = pluginI18n.tr("catalog.newExercise")
  val editExercise = pluginI18n.tr("catalog.editExercise")
  val name = pluginI18n.tr("catalog.name")
  val notes = pluginI18n.tr("catalog.notes")
  val custom = pluginI18n.tr("catalog.custom")
  val nothing = pluginI18n.tr("catalog.nothing")

  fun count(n: Int) =
    pluginI18n.plural("catalog.count", n)

  val technique = pluginI18n.tr("catalog.technique")
  val progress = pluginI18n.tr("catalog.progress")
  val history = pluginI18n.tr("catalog.history")
  val noHistory = pluginI18n.tr("catalog.noHistory")
  val savedMax = pluginI18n.tr("catalog.savedMax")
  val bestEstimate = pluginI18n.tr("catalog.bestEstimate")
  val sessions = pluginI18n.tr("catalog.sessions")
  val lastTime = pluginI18n.tr("catalog.lastTime")
  val never = pluginI18n.tr("catalog.never")

  fun bestSet(w: String, r: Int) = pluginI18n.tr("catalog.bestSet", "w" to w, "r" to r)

  fun setsCount(n: Int) =
    pluginI18n.plural("catalog.setsCount", n)

  fun volume(v: String) = pluginI18n.tr("catalog.volume", "v" to v)

  val hide = pluginI18n.tr("catalog.hide")
  val hiddenTitle = pluginI18n.tr("catalog.hiddenTitle")
  val showHidden = pluginI18n.tr("catalog.showHidden")
  val restore = pluginI18n.tr("catalog.restore")
  val exerciseWord = pluginI18n.tr("catalog.exerciseWord")
  val pick = pluginI18n.tr("catalog.pick")
  val pickTitle = pluginI18n.tr("catalog.pickTitle")
  val addFromCatalog = pluginI18n.tr("catalog.addFromCatalog")
  val add = pluginI18n.tr("catalog.add")
  val save = pluginI18n.tr("catalog.save")
  val cancel = pluginI18n.tr("catalog.cancel")
  val alreadyExists = pluginI18n.tr("catalog.alreadyExists")

  val howItLooks = pluginI18n.tr("catalog.howItLooks")
  val imagesLoading = pluginI18n.tr("catalog.imagesLoading")

  fun imagesFailed(reason: String) = pluginI18n.tr("catalog.imagesFailed", "reason" to reason)

  val imagesEmpty = pluginI18n.tr("catalog.imagesEmpty")
  val retry = pluginI18n.tr("catalog.retry")
  val noImage = pluginI18n.tr("catalog.noImage")
  val pickImage = pluginI18n.tr("catalog.pickImage")
  val changeImage = pluginI18n.tr("catalog.changeImage")
  val autoImage = pluginI18n.tr("catalog.autoImage")
  val removeImage = pluginI18n.tr("catalog.removeImage")
  val showSteps = pluginI18n.tr("catalog.showSteps")
  val hideSteps = pluginI18n.tr("catalog.hideSteps")
  val imagesCredit = pluginI18n.tr("catalog.imagesCredit")
  val pickImageTitle = pluginI18n.tr("catalog.pickImageTitle")
  val imageSearchHint = pluginI18n.tr("catalog.imageSearchHint")

  fun muscleLabel(m: String) =
    when (m) {
      MUSCLE_CHEST -> pluginI18n.tr("catalog.muscleLabel")
      MUSCLE_BACK -> pluginI18n.tr("catalog.muscleLabel.2")
      MUSCLE_SHOULDERS -> pluginI18n.tr("catalog.muscleLabel.3")
      MUSCLE_BICEPS -> pluginI18n.tr("catalog.muscleLabel.4")
      MUSCLE_TRICEPS -> pluginI18n.tr("catalog.muscleLabel.5")
      MUSCLE_LEGS -> pluginI18n.tr("catalog.muscleLabel.6")
      MUSCLE_GLUTES -> pluginI18n.tr("catalog.muscleLabel.7")
      MUSCLE_CALVES -> pluginI18n.tr("catalog.muscleLabel.8")
      MUSCLE_CORE -> pluginI18n.tr("catalog.muscleLabel.9")
      else -> pluginI18n.tr("catalog.muscleLabel.10")
    }()

  fun equipmentLabel(e: String) =
    when (e) {
      EQ_BARBELL -> pluginI18n.tr("catalog.equipmentLabel")
      EQ_DUMBBELL -> pluginI18n.tr("catalog.equipmentLabel.2")
      EQ_MACHINE -> pluginI18n.tr("catalog.equipmentLabel.3")
      EQ_CABLE -> pluginI18n.tr("catalog.equipmentLabel.4")
      EQ_BODYWEIGHT -> pluginI18n.tr("catalog.equipmentLabel.5")
      EQ_KETTLEBELL -> pluginI18n.tr("catalog.equipmentLabel.6")
      else -> pluginI18n.tr("catalog.equipmentLabel.7")
    }()
}
