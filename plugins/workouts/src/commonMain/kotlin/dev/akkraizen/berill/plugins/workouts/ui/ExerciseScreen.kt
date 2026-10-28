package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.cards.Stat
import dev.akkraizen.berill.api.ui.cards.StatTiles
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.dialogs.DeleteButton
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.Tone
import dev.akkraizen.berill.api.ui.fields.BodyEditor
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.workouts.data.CUSTOM_PREFIX
import dev.akkraizen.berill.plugins.workouts.data.ExerciseMode
import dev.akkraizen.berill.plugins.workouts.data.customExercises
import dev.akkraizen.berill.plugins.workouts.data.doneSets
import dev.akkraizen.berill.plugins.workouts.data.formatDuration
import dev.akkraizen.berill.plugins.workouts.data.formatKg
import dev.akkraizen.berill.plugins.workouts.data.logs
import dev.akkraizen.berill.plugins.workouts.data.maxFor
import dev.akkraizen.berill.plugins.workouts.data.mode
import dev.akkraizen.berill.plugins.workouts.data.roundTo
import dev.akkraizen.berill.plugins.workouts.data.sessions
import dev.akkraizen.berill.plugins.workouts.i18n.CatalogText
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText

@Composable
internal fun ExerciseScreen(ctx: PluginContext, id: String) {
  val catalog = rememberCatalog(ctx)
  val ex = catalog.find(id)
  val logCollection = remember(ctx) { logs(ctx) }
  val allLogs by remember(logCollection) { logCollection.observe() }.collectAsState(emptyList())
  val prefs = rememberWorkoutPrefs(ctx)
  var editing by remember { mutableStateOf(false) }

  if (ex == null) {
    PluginList { item { EmptyHint(CatalogText.nothing()) } }
    return
  }
  val sessions = ex.sessions(allLogs.map { it.value })
  val savedMax = ex.aliases.firstNotNullOfOrNull { prefs.maxes.maxFor(it) }
  val best = sessions.mapNotNull { it.best }.maxByOrNull { it.second }
  val customId = if (ex.custom) id.removePrefix(CUSTOM_PREFIX) else null
  val images = rememberExerciseImages(ctx)

  PluginList {
    item {
      DetailHeader(ex.name, Icons.Outlined.FitnessCenter) {
        if (customId != null) IconButton(onClick = { editing = true }) { Ic(BerillIcons.Edit) }
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillChip(onClick = null, label = { Text(CatalogText.muscleLabel(ex.muscle), maxLines = 1, overflow = TextOverflow.Ellipsis) })
        BerillChip(onClick = null, label = { Text(CatalogText.equipmentLabel(ex.equipment), maxLines = 1, overflow = TextOverflow.Ellipsis) })
        BerillChip(onClick = null, label = { Text(WorkoutsText.modeLabel(ex.mode), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          leadingIcon = { Ic(ex.mode.icon(), size = 18.dp) })
        if (ex.custom) BerillChip(onClick = null, label = { Text(CatalogText.custom(), maxLines = 1, overflow = TextOverflow.Ellipsis) })
      }
    }
    item {
      TwoPane(
        left = {
          ExerciseImageCard(ctx, ex, images)
          SectionCard(CatalogText.technique(), Icons.Outlined.MenuBook) {
            if (customId != null) {
              BodyEditor(
                ex.description.orEmpty(),
                { v ->
                  val c = customExercises(ctx)
                  c.get(customId)?.let { fresh ->
                    c.save(customId, fresh.copy(notes = v.ifBlank { null }))
                  }
                },
                placeholder = CatalogText.notes()
              )
            } else {
              Text(ex.description.orEmpty())
            }
          }
          SectionCard(CatalogText.progress(), Icons.Outlined.TrendingUp) {
            val timedSessions = sessions.filter { it.longestSeconds != null }
            if (timedSessions.isNotEmpty()) {
              StatTiles(listOf(
                Stat(WorkoutsText.longestDuration(), formatDuration(timedSessions.maxOf { it.longestSeconds!! }),
                  tone = Tone.Good, icon = Icons.Outlined.Timer),
                Stat(WorkoutsText.totalDuration(), formatDuration(timedSessions.sumOf { it.durationSeconds }),
                  icon = Icons.Outlined.HourglassEmpty),
                Stat(CatalogText.sessions(), timedSessions.size.toString(), icon = Icons.Outlined.History)
              ), minTileWidth = 130.dp)
              val durations = timedSessions.reversed().map { it.durationSeconds.toDouble() }
              if (durations.size >= 2) Sparkline(durations)
            }
            if (ex.mode == ExerciseMode.REPETITIONS) StatTiles(
              listOf(
                Stat(
                  CatalogText.savedMax(),
                  savedMax?.let { WorkoutsText.kgValue(formatKg(it))() }
                    ?: CatalogText.never()
                ),
                Stat(
                  CatalogText.bestEstimate(),
                  best?.let { WorkoutsText.kgValue(formatKg(roundTo(it.second, 0.5)))() }
                    ?: CatalogText.never(),
                  sub = best?.first?.let {
                    CatalogText.bestSet(formatKg(it.weight!!), it.reps!!)()
                  }
                ),
                Stat(CatalogText.sessions(), sessions.size.toString()),
                Stat(
                  CatalogText.lastTime(),
                  sessions.firstOrNull()?.date?.toDateOrNull()?.dayMonth()
                    ?: CatalogText.never()
                )
              ),
              minTileWidth = 130.dp
            )
            val points = sessions.reversed().mapNotNull { it.best?.second }
            if (points.size >= 2) Sparkline(points)
            if (best != null && savedMax == null) {
              TextButton(onClick = { setMax(ctx, ex.name, roundTo(best.second, 0.5)) }) {
                Text(WorkoutsText.saveAsMax())
              }
            }
          }
        },
        right = {
          SectionCard(CatalogText.history(), Icons.Outlined.History) {
            if (sessions.isEmpty()) EmptyHint(CatalogText.noHistory())
            sessions.take(30).forEach { s ->
              Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(s.date.toDateOrNull()?.dayMonth() ?: s.date, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    s.longestSeconds?.let { formatDuration(it) } ?: s.best?.first?.let {
                      CatalogText.bestSet(formatKg(it.weight!!), it.reps!!)()
                    } ?: CatalogText.setsCount(s.doneSets),
                    style = MaterialTheme.typography.titleSmall
                  )
                  Text(
                    listOfNotNull(
                      CatalogText.setsCount(s.doneSets),
                      s.durationSeconds.takeIf { it > 0 }?.let { WorkoutsText.durationValue(it)() },
                      s.volume
                        .takeIf { it > 0 }
                        ?.let { CatalogText.volume(formatKg(it))() }
                    )
                      .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        },
        leftWeight = 1f
      )
    }
    item {
      if (customId != null) {
        DeleteButton(CatalogText.exerciseWord()) {
          customExercises(ctx).delete(customId)
          ctx.nav.back()
        }
      } else {
        TextButton(
          onClick = {
            setHidden(ctx, ex.id, true)
            ctx.nav.back()
          }
        ) {
          Ic(Icons.Outlined.VisibilityOff, size = 18.dp)
          Spacer(Modifier.width(6.dp))
          Text(CatalogText.hide())
        }
      }
    }
  }

  if (editing && customId != null) {
    val current = customExercises(ctx).get(customId)
    if (current != null) {
      ExerciseDialog(
        initial = current,
        muscle = current.muscle,
        existing = catalog.visible.filter { it.id != id },
        onDismiss = { editing = false },
        onSave = { v ->
          val c = customExercises(ctx)
          c.get(customId)?.let { fresh ->
            c.save(customId, fresh.copy(name = v.name, muscle = v.muscle, equipment = v.equipment, mode = v.mode))
          }
          editing = false
        }
      )
    }
  }
}

@Composable
private fun Sparkline(values: List<Double>) {
  val color = MaterialTheme.colorScheme.primary
  val lo = values.min()
  val hi = values.max()
  val span = (hi - lo).takeIf { it > 0 } ?: 1.0
  Canvas(Modifier.fillMaxWidth().height(64.dp)) {
    val path = Path()
    values.forEachIndexed { i, v ->
      val x = size.width * i / (values.size - 1)
      val y = (size.height * (1 - (v - lo) / span)).toFloat()
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    val lastY = (size.height * (1 - (values.last() - lo) / span)).toFloat()
    drawCircle(color, 4.dp.toPx(), Offset(size.width, lastY))
  }
}
