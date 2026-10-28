package dev.akkraizen.berill.plugins.workouts.widgets

import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetButton
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetSize
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import dev.akkraizen.berill.plugins.workouts.data.Cycle
import dev.akkraizen.berill.plugins.workouts.data.LoggedExercise
import dev.akkraizen.berill.plugins.workouts.data.Program
import dev.akkraizen.berill.plugins.workouts.data.WorkoutLog
import dev.akkraizen.berill.plugins.workouts.data.cycles
import dev.akkraizen.berill.plugins.workouts.data.daysForWeek
import dev.akkraizen.berill.plugins.workouts.data.doneSets
import dev.akkraizen.berill.plugins.workouts.data.formatDuration
import dev.akkraizen.berill.plugins.workouts.data.formatKg
import dev.akkraizen.berill.plugins.workouts.data.logFromDay
import dev.akkraizen.berill.plugins.workouts.data.logFromPlan
import dev.akkraizen.berill.plugins.workouts.data.logs
import dev.akkraizen.berill.plugins.workouts.data.planned
import dev.akkraizen.berill.plugins.workouts.data.programs
import dev.akkraizen.berill.plugins.workouts.data.startWorkout
import dev.akkraizen.berill.plugins.workouts.data.totalSets
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText
import dev.akkraizen.berill.plugins.workouts.ui.weekNow
import dev.akkraizen.berill.plugins.workouts.ui.workoutPrefs

internal fun workoutsWidgets(ctx: PluginContext): List<WidgetProvider> = listOf(NextWorkoutWidget(ctx), RestTimerWidget())

internal data class NextWorkout(
  val title: String,
  val subtitle: String?,
  val source: String,
  val logTitle: String,
  val exercises: List<LoggedExercise>,
  val lines: List<String>
)

private fun exerciseLine(exercise: LoggedExercise): String {
  val first = exercise.sets.firstOrNull()
  val volume = when {
    first?.durationSeconds != null ->
      if (exercise.durationPerSet) "${exercise.sets.size} × ${formatDuration(first.durationSeconds)}" else formatDuration(first.durationSeconds)
    else -> WorkoutsText.setsReps(exercise.sets.size, first?.reps?.toString() ?: "—")()
  }
  val weight = first?.weight?.let { WorkoutsText.kgValue(formatKg(it))() }
  return listOfNotNull(exercise.name.ifBlank { WorkoutsText.exercise() } + " " + volume, weight).joinToString(" · ")
}

internal fun nextWorkout(ctx: PluginContext): NextWorkout? {
  val prefs = workoutPrefs(ctx)
  val history = logs(ctx).all()
  val activeCycle = cycles(ctx).all().filter { it.value.weekNow() != null }.maxByOrNull { it.value.createdAt }
  if (activeCycle != null) fromCycle(activeCycle, history, prefs.maxes, prefs.rounding)?.let { return it }
  val all = programs(ctx).all()
  if (all.isEmpty()) return null
  val lastProgramLog = history
    .filter { it.value.source?.startsWith("program:") == true }
    .maxByOrNull { it.value.createdAt }
  val parts = lastProgramLog?.value?.source?.split(':')
  val program = parts?.getOrNull(1)?.let { id -> all.firstOrNull { it.id == id } }
    ?: all.maxByOrNull { it.value.createdAt }
    ?: return null
  if (program.value.days.isEmpty()) return null
  val dayIndex = if (parts?.getOrNull(1) == program.id) ((parts.getOrNull(2)?.toIntOrNull() ?: -1) + 1) % program.value.days.size else 0
  return fromProgram(program, dayIndex, prefs.maxes, prefs.rounding)
}

private fun fromProgram(program: Item<Program>, dayIndex: Int, maxes: Map<String, Double>, rounding: Double): NextWorkout {
  val day = program.value.days[dayIndex]
  val exercises = logFromDay(day, maxes, rounding)
  return NextWorkout(
    title = day.title.ifBlank { WorkoutsText.dayTitle(dayIndex + 1)() },
    subtitle = program.value.title.ifBlank { null },
    source = "program:${program.id}:$dayIndex",
    logTitle = WorkoutsText.fromProgram(program.value.title, day.title)(),
    exercises = exercises,
    lines = exercises.map(::exerciseLine)
  )
}

private fun fromCycle(cycle: Item<Cycle>, history: List<Item<WorkoutLog>>, maxes: Map<String, Double>, rounding: Double): NextWorkout? {
  val c = cycle.value
  val week = c.weekNow() ?: return null
  val days = c.daysForWeek(week)
  if (days.isEmpty()) return null
  val prefix = "cycle:${cycle.id}:$week:"
  val done = history.mapNotNull { it.value.source?.takeIf { s -> s.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull() }.toSet()
  val dayIndex = days.indices.firstOrNull { it !in done } ?: return null
  val day = days[dayIndex]
  val plannedExercises = day.exercises.indices.map { c.planned(week, dayIndex, it, maxes, rounding) }
  val exercises = logFromPlan(plannedExercises)
  return NextWorkout(
    title = day.title.ifBlank { WorkoutsText.dayTitle(dayIndex + 1)() },
    subtitle = listOf(c.title.ifBlank { WorkoutsText.pageCycle() }, WorkoutsText.week(week)()).joinToString(" · "),
    source = "$prefix$dayIndex",
    logTitle = WorkoutsText.fromCycle(c.title, week, day.title)(),
    exercises = exercises,
    lines = exercises.map(::exerciseLine)
  )
}

private fun unfinishedToday(ctx: PluginContext): Item<WorkoutLog>? =
  logs(ctx).all().filter { it.value.date == today().toString() && !it.value.finished }.maxByOrNull { it.value.createdAt }

private class NextWorkoutWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "next"
  override val title: Tr = WorkoutsText.widgetNextTitle
  override val description: Tr = WorkoutsText.widgetNextDescription
  override val size = WidgetSize.Medium

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val current = unfinishedToday(ctx)
    if (current != null) {
      val log = current.value
      return WidgetContent(
        title = log.title.ifBlank { WorkoutsText.workoutTitle() },
        subtitle = WorkoutsText.widgetProgress(log.doneSets(), log.totalSets())(),
        icon = WidgetIcon.Fitness,
        open = WidgetAction.Open(route = WorkoutsPlugin.LOG_ROUTE + current.id, page = WorkoutsPlugin.PAGE_JOURNAL),
        blocks = listOf(
          WidgetBlock.Bar(WorkoutsText.widgetInProgress(), "${log.doneSets()}/${log.totalSets()}", if (log.totalSets() == 0) 0f else log.doneSets().toFloat() / log.totalSets()),
          WidgetBlock.Rows(log.exercises.map { WidgetRow(exerciseLine(it), trailing = "${it.sets.count { s -> s.done }}/${it.sets.size}") }),
          WidgetBlock.Buttons(
            listOf(
              WidgetButton(
                WorkoutsText.widgetContinue(),
                WidgetAction.Open(route = WorkoutsPlugin.LOG_ROUTE + current.id, page = WorkoutsPlugin.PAGE_JOURNAL),
                WidgetIcon.Play,
                primary = true
              )
            )
          )
        )
      )
    }
    val next = nextWorkout(ctx)
      ?: return WidgetContent(
        title = title(),
        icon = WidgetIcon.Fitness,
        open = WidgetAction.Open(page = WorkoutsPlugin.PAGE_PROGRAM),
        blocks = listOf(
          WidgetBlock.Text(WorkoutsText.widgetNothingPlanned(), WidgetTone.Muted),
          WidgetBlock.Buttons(listOf(WidgetButton(WorkoutsText.quickWorkout(), WidgetAction.Run(QUICK, opensApp = true), WidgetIcon.Play)))
        )
      )
    return WidgetContent(
      title = next.title,
      subtitle = next.subtitle,
      icon = WidgetIcon.Fitness,
      open = WidgetAction.Open(page = WorkoutsPlugin.PAGE_CYCLE),
      blocks = listOf(
        WidgetBlock.Rows(next.lines.map { WidgetRow(it) }),
        WidgetBlock.Buttons(listOf(WidgetButton(WorkoutsText.widgetStart(), WidgetAction.Run(START, opensApp = true), WidgetIcon.Play, primary = true)))
      ),
      empty = WorkoutsText.widgetNoExercises()
    )
  }

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect =
    when (action.id) {
      START -> {
        val next = nextWorkout(ctx)
        if (next == null) {
          WidgetEffect.Refresh()
        } else {
          startWorkout(ctx, next.logTitle, next.source, next.exercises)
          WidgetEffect.OpenApp()
        }
      }
      QUICK -> {
        startWorkout(ctx, WorkoutsText.workoutTitle(), null, emptyList())
        WidgetEffect.OpenApp()
      }
      else -> WidgetEffect.Refresh()
    }

  override suspend fun summary(request: WidgetRequest): WidgetRow? {
    unfinishedToday(ctx)?.let { current ->
      return WidgetRow(
        title = WorkoutsText.title(),
        subtitle = current.value.title,
        trailing = "${current.value.doneSets()}/${current.value.totalSets()}",
        action = WidgetAction.Open(pluginId = WorkoutsPlugin.id, route = WorkoutsPlugin.LOG_ROUTE + current.id, page = WorkoutsPlugin.PAGE_JOURNAL),
        tone = WidgetTone.Accent,
        icon = WidgetIcon.Fitness
      )
    }
    val next = nextWorkout(ctx) ?: return null
    return WidgetRow(
      title = WorkoutsText.title(),
      subtitle = listOfNotNull(next.title, next.subtitle).joinToString(" · "),
      action = WidgetAction.Open(pluginId = WorkoutsPlugin.id, page = WorkoutsPlugin.PAGE_CYCLE),
      icon = WidgetIcon.Fitness
    )
  }

  private companion object {
    const val START = "start"
    const val QUICK = "quick"
  }
}

internal const val REST_ENDS_AT = "endsAt"
internal val REST_PRESETS = listOf(60, 90, 120, 180)

internal class RestTimerWidget : WidgetProvider {
  override val id = "rest"
  override val title: Tr = WorkoutsText.widgetRestTitle
  override val description: Tr = WorkoutsText.widgetRestDescription
  override val size = WidgetSize.Small

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val endsAt = request.state[REST_ENDS_AT]?.toLongOrNull()?.takeIf { it > request.nowMillis }
    return WidgetContent(
      title = title(),
      icon = WidgetIcon.Timer,
      open = WidgetAction.Open(page = WorkoutsPlugin.PAGE_JOURNAL),
      blocks = listOf(
        WidgetBlock.Countdown(endsAt, WorkoutsText.widgetResting(), WorkoutsText.widgetRestIdle()),
        WidgetBlock.Buttons(
          REST_PRESETS.map { seconds ->
            WidgetButton(formatDuration(seconds), WidgetAction.Run(START, mapOf(SECONDS to seconds.toString())), WidgetIcon.Timer)
          } + listOfNotNull(endsAt?.let { WidgetButton(WorkoutsText.widgetStop(), WidgetAction.Run(STOP), WidgetIcon.Stop) })
        )
      )
    )
  }

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect =
    when (action.id) {
      START -> {
        val seconds = action.args[SECONDS]?.toIntOrNull()?.coerceIn(1, MAX_SECONDS) ?: REST_PRESETS.first()
        val endsAt = request.nowMillis + seconds * 1000L
        WidgetEffect.Refresh(state = mapOf(REST_ENDS_AT to endsAt.toString()), refreshAtMillis = endsAt)
      }
      STOP -> WidgetEffect.Refresh(state = emptyMap())
      else -> WidgetEffect.Refresh()
    }

  private companion object {
    const val START = "start"
    const val STOP = "stop"
    const val SECONDS = "seconds"
    const val MAX_SECONDS = 3600
  }
}
