package dev.akkraizen.berill

import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.plugins.books.BooksPlugin
import dev.akkraizen.berill.plugins.finance.FinancePlugin
import dev.akkraizen.berill.plugins.habits.HabitsPlugin
import dev.akkraizen.berill.plugins.languages.LanguagesPlugin
import dev.akkraizen.berill.plugins.nutrition.NutritionPlugin
import dev.akkraizen.berill.plugins.planner.PlannerPlugin
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import dev.akkraizen.berill.plugins.recipes.RecipesPlugin
import dev.akkraizen.berill.plugins.study.StudyPlugin
import dev.akkraizen.berill.plugins.watching.WatchingPlugin
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin

val BerillPlugins: List<BerillPlugin> = listOf(
  HabitsPlugin,
  PlannerPlugin,
  BooksPlugin,
  WatchingPlugin,
  FinancePlugin,
  LanguagesPlugin,
  RecipesPlugin,
  StudyPlugin,
  ProjectsPlugin,
  WorkoutsPlugin,
  NutritionPlugin
)
