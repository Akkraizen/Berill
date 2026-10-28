package dev.akkraizen.berill.widgets

internal class WidgetSpec(val key: String?, val receiver: Class<out BerillWidgetReceiver>)

internal object WidgetCatalog {
  val specs: List<WidgetSpec> = listOf(
    WidgetSpec("core.today", TodayWidgetReceiver::class.java),
    WidgetSpec("core.note_quick", QuickNoteWidgetReceiver::class.java),
    WidgetSpec("core.note_pinned", PinnedNoteWidgetReceiver::class.java),
    WidgetSpec("core.journal", JournalWidgetReceiver::class.java),
    WidgetSpec("habits.today", HabitsTodayWidgetReceiver::class.java),
    WidgetSpec("habits.week", HabitsWeekWidgetReceiver::class.java),
    WidgetSpec("planner.today", PlannerTodayWidgetReceiver::class.java),
    WidgetSpec("planner.calendar", CalendarWidgetReceiver::class.java),
    WidgetSpec("planner.agenda", AgendaWidgetReceiver::class.java),
    WidgetSpec("workouts.next", NextWorkoutWidgetReceiver::class.java),
    WidgetSpec("workouts.rest", RestTimerWidgetReceiver::class.java),
    WidgetSpec("nutrition.calories", CaloriesWidgetReceiver::class.java),
    WidgetSpec("nutrition.water", WaterWidgetReceiver::class.java),
    WidgetSpec("nutrition.weight", WeightWidgetReceiver::class.java),
    WidgetSpec("books.reading", ReadingWidgetReceiver::class.java),
    WidgetSpec("watching.now", WatchingWidgetReceiver::class.java),
    WidgetSpec("languages.card", FlashcardWidgetReceiver::class.java),
    WidgetSpec("study.deadlines", DeadlinesWidgetReceiver::class.java),
    WidgetSpec("finance.month", FinanceMonthWidgetReceiver::class.java),
    WidgetSpec("finance.quick", QuickExpenseWidgetReceiver::class.java),
    WidgetSpec("recipes.day", RecipeOfDayWidgetReceiver::class.java),
    WidgetSpec(null, AnyWidgetReceiver::class.java)
  )

  fun spec(receiverClassName: String): WidgetSpec? = specs.firstOrNull { it.receiver.name == receiverClassName }
}
