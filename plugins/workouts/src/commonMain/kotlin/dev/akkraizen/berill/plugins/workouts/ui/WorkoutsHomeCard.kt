package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.Stat
import dev.akkraizen.berill.api.ui.cards.StatTiles
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.mondayOfWeek
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.workouts.data.logs
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText

@Composable
internal fun WorkoutsHomeCard(ctx: PluginContext) {
  val collection = remember(ctx) { logs(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  val monday = today().mondayOfWeek()
  val thisWeek = all.count { log -> log.value.date.toDateOrNull()?.let { it >= monday } == true }
  val last = all.maxByOrNull { it.value.date + it.value.createdAt.toString().padStart(15, '0') }?.value
  StatTiles(
    listOf(
      Stat(WorkoutsText.thisWeek(), "$thisWeek", icon = Icons.Outlined.FitnessCenter),
      Stat(
        WorkoutsText.lastWorkout(),
        last?.date?.toDateOrNull()?.dayMonth() ?: "—",
        sub = last?.title,
        icon = Icons.Outlined.EventNote
      )
    ),
    minTileWidth = 140.dp
  )
  if (all.isEmpty())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        WorkoutsText.noLogs(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
}
