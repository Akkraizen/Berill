package dev.akkraizen.berill.widgets

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

abstract class BerillWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = BerillGlanceWidget()
}

class TodayWidgetReceiver : BerillWidgetReceiver()

class QuickNoteWidgetReceiver : BerillWidgetReceiver()

class PinnedNoteWidgetReceiver : BerillWidgetReceiver()

class JournalWidgetReceiver : BerillWidgetReceiver()

class HabitsTodayWidgetReceiver : BerillWidgetReceiver()

class HabitsWeekWidgetReceiver : BerillWidgetReceiver()

class PlannerTodayWidgetReceiver : BerillWidgetReceiver()

class CalendarWidgetReceiver : BerillWidgetReceiver()

class AgendaWidgetReceiver : BerillWidgetReceiver()

class NextWorkoutWidgetReceiver : BerillWidgetReceiver()

class RestTimerWidgetReceiver : BerillWidgetReceiver()

class CaloriesWidgetReceiver : BerillWidgetReceiver()

class WaterWidgetReceiver : BerillWidgetReceiver()

class WeightWidgetReceiver : BerillWidgetReceiver()

class ReadingWidgetReceiver : BerillWidgetReceiver()

class WatchingWidgetReceiver : BerillWidgetReceiver()

class FlashcardWidgetReceiver : BerillWidgetReceiver()

class DeadlinesWidgetReceiver : BerillWidgetReceiver()

class FinanceMonthWidgetReceiver : BerillWidgetReceiver()

class QuickExpenseWidgetReceiver : BerillWidgetReceiver()

class RecipeOfDayWidgetReceiver : BerillWidgetReceiver()

class AnyWidgetReceiver : BerillWidgetReceiver()
