package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import dev.akkraizen.berill.plugins.workouts.i18n.pluginI18n
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import dev.akkraizen.berill.plugins.workouts.data.WorkoutLog
import dev.akkraizen.berill.plugins.workouts.data.doneSets
import dev.akkraizen.berill.plugins.workouts.data.formatKg
import dev.akkraizen.berill.plugins.workouts.data.logs
import dev.akkraizen.berill.plugins.workouts.data.startWorkout
import dev.akkraizen.berill.plugins.workouts.data.tonnage
import dev.akkraizen.berill.plugins.workouts.data.totalSets
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText

@Composable
internal fun JournalPage(ctx: PluginContext) {
  val route by ctx.nav.route.collectAsState()
  AnimatedRoute(route) { r ->
    if (r != null && r.startsWith(WorkoutsPlugin.LOG_ROUTE))
      WorkoutScreen(ctx, r.removePrefix(WorkoutsPlugin.LOG_ROUTE))
    else
      JournalList(ctx)
  }
}

private val newestFirst =
  compareByDescending<Item<WorkoutLog>> { it.value.date }.thenByDescending { it.value.createdAt }

private enum class JournalTab { RECORDS, STATISTICS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalList(ctx: PluginContext) {
  val collection = remember(ctx) { logs(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  var selected by rememberSaveable { mutableStateOf(JournalTab.RECORDS) }
  val savedTabs = rememberSaveableStateHolder()

  Column(Modifier.fillMaxSize()) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
      DetailHeader(WorkoutsText.journal(), Icons.Outlined.EventNote) {
        FilledTonalButton(onClick = { startWorkout(ctx, WorkoutsText.workoutTitle(), null, emptyList()) }) {
          Ic(BerillIcons.Add, size = 18.dp)
          Spacer(Modifier.width(8.dp))
          Text(WorkoutsText.emptyWorkout())
        }
      }
    }
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
      JournalTab.entries.forEach { tab ->
        Tab(selected = selected == tab, onClick = { selected = tab },
          text = { Text(pluginI18n.text("workouts.journal.tab.${tab.name}")) })
      }
    }
    savedTabs.SaveableStateProvider(selected.name) {
      when (selected) {
        JournalTab.RECORDS -> PluginList(Modifier.weight(1f)) {
          val sorted = all.sortedWith(newestFirst)
          if (sorted.isEmpty()) item { SectionCard { EmptyHint(WorkoutsText.noLogs()) } }
          items(sorted, key = { it.id }) { item ->
            LogRow(item.value) { ctx.nav.open(WorkoutsPlugin.LOG_ROUTE + item.id) }
          }
        }
        JournalTab.STATISTICS -> {
          val catalog = rememberCatalog(ctx)
          PluginList(Modifier.weight(1f)) {
            item { dev.akkraizen.berill.api.ui.display.HealthSummary(ctx) }
            item { MuscleBalanceCard(all.map { it.value }, catalog.visible + catalog.hidden) }
            item { WorkoutStatistics(all.map { it.value }) }
          }
        }
      }
    }
  }
}

@Composable
private fun LogRow(log: WorkoutLog, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Surface(
    color = colors.surfaceContainerHigh,
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth().interactive(MaterialTheme.shapes.large, onClick = onClick)
  ) {
    Row(
      Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Column(Modifier.widthIn(min = 72.dp)) {
        Text(log.date.toDateOrNull()?.dayMonth() ?: log.date, style = MaterialTheme.typography.titleSmall)
      }
      Column(Modifier.weight(1f)) {
        Text(log.title.ifBlank { WorkoutsText.workoutTitle() }, style = MaterialTheme.typography.titleMedium)
        Text(
          listOf(
            WorkoutsText.exercises(log.exercises.size),
            WorkoutsText.setsDone(log.doneSets(), log.totalSets())(),
            WorkoutsText.tonnage(formatKg(log.tonnage()))()
          )
            .joinToString(" · "),
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant
        )
      }
      Text(
        if (log.finished) WorkoutsText.finished() else WorkoutsText.inProgress(),
        style = MaterialTheme.typography.labelLarge,
        color = if (log.finished) colors.primary else colors.tertiary
      )
    }
  }
}
