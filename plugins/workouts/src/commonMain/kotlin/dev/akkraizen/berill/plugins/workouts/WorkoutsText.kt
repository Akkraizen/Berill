package dev.akkraizen.berill.plugins.workouts

internal object WorkoutsText {
  val dayLimit = pluginI18n.tr("workouts.dayLimit")
  val assignProgram = pluginI18n.tr("workouts.assignProgram")
  val resetWeekProgram = pluginI18n.tr("workouts.resetWeekProgram")
  val assignHint = pluginI18n.tr("workouts.assignHint")
  val chooseProgram = pluginI18n.tr("workouts.chooseProgram")
  val chooseWeeks = pluginI18n.tr("workouts.chooseWeeks")
  val applyProgram = pluginI18n.tr("workouts.applyProgram")

  val title = pluginI18n.tr("workouts.title")
  val description = pluginI18n.tr("workouts.description")
  val pageProgram = pluginI18n.tr("workouts.pageProgram")
  val pageCycle = pluginI18n.tr("workouts.pageCycle")
  val pageJournal = pluginI18n.tr("workouts.pageJournal")
  val pageCalculator = pluginI18n.tr("workouts.pageCalculator")
  val homeCard = pluginI18n.tr("workouts.homeCard")
  val quickWorkout = pluginI18n.tr("workouts.quickWorkout")

  val kg = pluginI18n.tr("workouts.kg")

  fun kgValue(value: String) = pluginI18n.tr("workouts.kgValue", "value" to value)

  val add = pluginI18n.tr("workouts.add")
  val create = pluginI18n.tr("workouts.create")
  val cancel = pluginI18n.tr("workouts.cancel")
  val save = pluginI18n.tr("workouts.save")
  val delete = pluginI18n.tr("workouts.delete")
  val name = pluginI18n.tr("workouts.name")
  val start = pluginI18n.tr("workouts.start")
  val edit = pluginI18n.tr("workouts.edit")
  val notes = pluginI18n.tr("workouts.notes")
  val noWeight = pluginI18n.tr("workouts.noWeight")

  fun days(n: Int) =
    pluginI18n.plural("workouts.days", n)

  fun exercises(n: Int) =
    pluginI18n.plural("workouts.exercises", n)

  fun weeks(n: Int) =
    pluginI18n.plural("workouts.weeks", n)

  val exercise = pluginI18n.tr("workouts.exercise")
  val exerciseType = pluginI18n.tr("workouts.exerciseType")
  val durationInput = pluginI18n.tr("workouts.durationInput")
  val restDuration = pluginI18n.tr("workouts.restDuration")
  val longestDuration = pluginI18n.tr("workouts.longestDuration")
  val totalDuration = pluginI18n.tr("workouts.totalDuration")
  val timedSets = pluginI18n.tr("workouts.timedSets")
  val repetitions = pluginI18n.tr("workouts.repetitions")
  val timer = pluginI18n.tr("workouts.timer")
  val pauseTimer = pluginI18n.tr("workouts.pauseTimer")
  val stopTimer = pluginI18n.tr("workouts.stopTimer")
  val rest = pluginI18n.tr("workouts.rest")
  val timerDone = pluginI18n.tr("workouts.timerDone")
  fun modeLabel(mode: ExerciseMode): String = when (mode) {
    ExerciseMode.REPETITIONS -> repetitions()
    ExerciseMode.DURATION -> byTime()
    ExerciseMode.TIMED_SETS -> timedSets()
  }
  fun timingSummary(sets: Int, seconds: Int, perSet: Boolean): String =
    if (perSet) "$sets × ${formatDuration(seconds)}" else durationValue(seconds)()

  val byTime = pluginI18n.tr("workouts.byTime")
  val durationSeconds = pluginI18n.tr("workouts.durationSeconds")
  fun durationValue(seconds: Int) = pluginI18n.tr("workouts.durationValue", "seconds" to formatDuration(seconds))
  val sets = pluginI18n.tr("workouts.sets")
  val reps = pluginI18n.tr("workouts.reps")
  val weightKg = pluginI18n.tr("workouts.weightKg")
  val percentOfMax = pluginI18n.tr("workouts.percentOfMax")
  val addExercise = pluginI18n.tr("workouts.addExercise")
  val addDay = pluginI18n.tr("workouts.addDay")

  fun dayTitle(n: Int) = pluginI18n.tr("workouts.dayTitle", "p0" to 'A' + (n - 1).coerceIn(0, 25))

  val dayName = pluginI18n.tr("workouts.dayName")
  val deleteDay = pluginI18n.tr("workouts.deleteDay")
  val deleteExercise = pluginI18n.tr("workouts.deleteExercise")
  val moveUp = pluginI18n.tr("workouts.moveUp")
  val moveDown = pluginI18n.tr("workouts.moveDown")
  val byWeight = pluginI18n.tr("workouts.byWeight")
  val byPercent = pluginI18n.tr("workouts.byPercent")

  fun setsReps(sets: Int, reps: String) = pluginI18n.tr("workouts.setsReps", "sets" to sets, "reps" to reps)

  val noExercises = pluginI18n.tr("workouts.noExercises")

  fun deleteDayQuestion(day: String) = pluginI18n.tr("workouts.deleteDayQuestion", "day" to day)

  val deleteDayText =
    pluginI18n.tr("workouts.deleteDayText")

  val programs = pluginI18n.tr("workouts.programs")
  val newProgram = pluginI18n.tr("workouts.newProgram")
  val noPrograms = pluginI18n.tr("workouts.noPrograms")
  val programWord = pluginI18n.tr("workouts.programWord")
  val startDay = pluginI18n.tr("workouts.startDay")
  val programSummaryEmpty = pluginI18n.tr("workouts.programSummaryEmpty")
  val programNotes = pluginI18n.tr("workouts.programNotes")

  val cycles = pluginI18n.tr("workouts.cycles")
  val newCycle = pluginI18n.tr("workouts.newCycle")
  val noCycles = pluginI18n.tr("workouts.noCycles")
  val cycleWord = pluginI18n.tr("workouts.cycleWord")
  val weeksCount = pluginI18n.tr("workouts.weeksCount")
  val startDate = pluginI18n.tr("workouts.startDate")
  val progression = pluginI18n.tr("workouts.progression")
  val progressionKg = pluginI18n.tr("workouts.progressionKg")
  val progressionPercent = pluginI18n.tr("workouts.progressionPercent")
  val progressionNone = pluginI18n.tr("workouts.progressionNone")
  val stepKg = pluginI18n.tr("workouts.stepKg")
  val stepPercent = pluginI18n.tr("workouts.stepPercent")
  val repsStep = pluginI18n.tr("workouts.repsStep")
  val deloadLast = pluginI18n.tr("workouts.deloadLast")
  val deloadHint = pluginI18n.tr("workouts.deloadHint")
  val template = pluginI18n.tr("workouts.template")
  val templateHint = pluginI18n.tr("workouts.templateHint")

  fun week(n: Int) = pluginI18n.tr("workouts.week", "n" to n)

  val currentWeek = pluginI18n.tr("workouts.currentWeek")
  val deloadWeek = pluginI18n.tr("workouts.deloadWeek")
  val noMax = pluginI18n.tr("workouts.noMax")
  val overridden = pluginI18n.tr("workouts.overridden")

  fun overrideTitle(exercise: String, week: Int) = pluginI18n.tr("workouts.overrideTitle", "exercise" to exercise, "week" to week)

  val overrideHint = pluginI18n.tr("workouts.overrideHint")
  val resetOverride = pluginI18n.tr("workouts.resetOverride")

  fun planned(value: String) = pluginI18n.tr("workouts.planned", "value" to value)

  val weekPlan = pluginI18n.tr("workouts.weekPlan")

  val journal = pluginI18n.tr("workouts.journal")
  val emptyWorkout = pluginI18n.tr("workouts.emptyWorkout")
  val noLogs = pluginI18n.tr("workouts.noLogs")
  val workoutWord = pluginI18n.tr("workouts.workoutWord")
  val workoutTitle = pluginI18n.tr("workouts.workoutTitle")
  val date = pluginI18n.tr("workouts.date")

  fun setsDone(done: Int, total: Int) = pluginI18n.tr("workouts.setsDone", "done" to done, "total" to total)

  fun tonnage(value: String) = pluginI18n.tr("workouts.tonnage", "value" to value)

  val finished = pluginI18n.tr("workouts.finished")
  val inProgress = pluginI18n.tr("workouts.inProgress")
  val finish = pluginI18n.tr("workouts.finish")
  val resume = pluginI18n.tr("workouts.resume")
  val addSet = pluginI18n.tr("workouts.addSet")
  val removeSet = pluginI18n.tr("workouts.removeSet")

  fun setNumber(n: Int) = pluginI18n.tr("workouts.setNumber", "n" to n)

  val setDone = pluginI18n.tr("workouts.setDone")
  val markAll = pluginI18n.tr("workouts.markAll")

  fun bestSet(
    weight: String,
    reps: Int,
    e1rm: String
  ) = pluginI18n.tr("workouts.bestSet", "weight" to weight, "reps" to reps, "e1rm" to e1rm)

  val saveAsMax = pluginI18n.tr("workouts.saveAsMax")
  val thisWeek = pluginI18n.tr("workouts.thisWeek")
  val weekTonnage = pluginI18n.tr("workouts.weekTonnage")
  val workoutsStat = pluginI18n.tr("workouts.workoutsStat")
  val lastWorkout = pluginI18n.tr("workouts.lastWorkout")
  val never = pluginI18n.tr("workouts.never")

  fun fromCycle(
    cycle: String,
    week: Int,
    day: String
  ) = pluginI18n.tr("workouts.fromCycle", "cycle" to cycle, "week" to week, "day" to day)

  fun fromProgram(program: String, day: String) = pluginI18n.tr("workouts.fromProgram", "program" to program, "day" to day)

  val oneRepMax = pluginI18n.tr("workouts.oneRepMax")
  val oneRepMaxHint = pluginI18n.tr("workouts.oneRepMaxHint")
  val liftedWeight = pluginI18n.tr("workouts.liftedWeight")
  val liftedReps = pluginI18n.tr("workouts.liftedReps")

  fun estimatedMax(value: String) = pluginI18n.tr("workouts.estimatedMax", "value" to value)

  val percentTable = pluginI18n.tr("workouts.percentTable")

  fun repsApprox(n: Int) = pluginI18n.tr("workouts.repsApprox", "n" to n)

  val saveMaxFor = pluginI18n.tr("workouts.saveMaxFor")
  val maxes = pluginI18n.tr("workouts.maxes")
  val maxesHint = pluginI18n.tr("workouts.maxesHint")
  val noMaxes = pluginI18n.tr("workouts.noMaxes")
  val plates = pluginI18n.tr("workouts.plates")
  val targetWeight = pluginI18n.tr("workouts.targetWeight")
  val barWeight = pluginI18n.tr("workouts.barWeight")
  val perSide = pluginI18n.tr("workouts.perSide")
  val justBar = pluginI18n.tr("workouts.justBar")

  fun remainder(value: String) = pluginI18n.tr("workouts.remainder", "value" to value)

  fun totalLoaded(value: String) = pluginI18n.tr("workouts.totalLoaded", "value" to value)

  val equipment = pluginI18n.tr("workouts.equipment")
  val defaultBar = pluginI18n.tr("workouts.defaultBar")
  val availablePlates = pluginI18n.tr("workouts.availablePlates")
  val platePlaceholder = pluginI18n.tr("workouts.platePlaceholder")
  val rounding = pluginI18n.tr("workouts.rounding")
}
