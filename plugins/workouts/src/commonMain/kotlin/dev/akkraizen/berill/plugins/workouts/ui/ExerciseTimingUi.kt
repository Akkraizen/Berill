package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.fields.rememberCommitState
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.workouts.data.ExerciseMode
import dev.akkraizen.berill.plugins.workouts.data.WorkoutTimer
import dev.akkraizen.berill.plugins.workouts.data.formatDuration
import dev.akkraizen.berill.plugins.workouts.data.mode
import dev.akkraizen.berill.plugins.workouts.data.normalize
import dev.akkraizen.berill.plugins.workouts.data.parseDuration
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText

internal fun ExerciseMode.icon(): ImageVector = when (this) {
  ExerciseMode.REPETITIONS -> Icons.Outlined.FitnessCenter
  ExerciseMode.DURATION -> Icons.Outlined.Timer
  ExerciseMode.TIMED_SETS -> Icons.Outlined.HourglassEmpty
}

@Composable
internal fun ExerciseModeChips(mode: ExerciseMode, onChange: (ExerciseMode) -> Unit) {
  ChoiceChips(
    ExerciseMode.entries.toList(), mode, WorkoutsText::modeLabel, onChange,
    modifier = Modifier.fillMaxWidth(), icon = { it.icon() })
}

@Composable
internal fun DurationField(
  seconds: Int,
  onChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  label: String = WorkoutsText.durationInput(),
  allowZero: Boolean = false
) {
  fun valid(text: String) = parseDuration(text)?.let { allowZero || it > 0 } == true
  val state = rememberCommitState(
    formatDuration(seconds),
    normalize = { parseDuration(it)?.let(::formatDuration) ?: it.trim() },
    onCommit = { if (valid(it)) parseDuration(it)?.let(onChange) })
  BerillTextField(
    state.text, { state.text = it }, modifier.fillMaxWidth(),
    label = { Text(label) },
    leadingIcon = { Ic(if (allowZero) Icons.Outlined.SelfImprovement else Icons.Outlined.Timer) },
    textStyle = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
    isError = !valid(state.text), placeholder = { Text("0:45", maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
    shape = MaterialTheme.shapes.medium
  )
}

internal class WorkoutTimerState(initial: WorkoutTimer? = null) {
  var value by mutableStateOf(initial)

  companion object {
    val Saver = listSaver<WorkoutTimerState, Any>(
      save = { state ->
        state.value?.let {
          listOf(it.exercise, it.set, it.remainingMillis, it.deadlineMillis ?: -1L, it.resting)
        } ?: emptyList()
      },
      restore = { values ->
        WorkoutTimerState(
          if (values.isEmpty()) null else WorkoutTimer(
            values[0] as Int, values[1] as Int, values[2] as Long,
            (values[3] as Long).takeIf { it >= 0 }, values[4] as Boolean
          )
        )
      }
    )
  }
}

@Composable
internal fun WorkoutTimerPanel(
  timer: WorkoutTimer, now: Long, exercise: String, totalSeconds: Int,
  onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val paused = timer.deadlineMillis == null
  val container = if (timer.resting) colors.tertiaryContainer else colors.primaryContainer
  val content = if (timer.resting) colors.onTertiaryContainer else colors.onPrimaryContainer
  val accent = if (timer.resting) colors.tertiary else colors.primary
  Surface(
    Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
    color = container, contentColor = content
  ) {
    Column(
      Modifier.animateContentSize(BerillMotion.spatial()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Ic(
          if (timer.resting) Icons.Outlined.SelfImprovement else Icons.Outlined.Timer,
          size = 24.dp
        )
        Column(Modifier.weight(1f)) {
          Text(
            if (timer.resting) WorkoutsText.rest() else WorkoutsText.timer(),
            style = MaterialTheme.typography.labelLarge
          )
          Text(
            exercise, style = MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis
          )
        }
        BerillChip(
          onClick = null, selected = true,
          label = { Text(if (paused) WorkoutsText.pauseTimer() else WorkoutsText.inProgress()) },
          leadingIcon = {
            Ic(
              if (paused) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
              size = 16.dp
            )
          })
      }
      Text(
        formatDuration(((timer.remaining(now) + 999) / 1000).toInt()),
        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum")
      )
      ProgressLine(
        timer.remaining(now).toFloat() / (totalSeconds.coerceAtLeast(1).toLong() * 1000),
        color = accent
      )
      if (!timer.resting) Text(
        WorkoutsText.setNumber(timer.set + 1)(),
        style = MaterialTheme.typography.labelLarge
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        FilledTonalButton(onClick = if (paused) onResume else onPause) {
          Ic(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, size = 18.dp)
          Spacer(Modifier.width(6.dp))
          Text(if (paused) WorkoutsText.resume() else WorkoutsText.pauseTimer())
        }
        OutlinedButton(onClick = onStop) {
          Ic(Icons.Outlined.RestartAlt, size = 18.dp)
          Spacer(Modifier.width(6.dp))
          Text(WorkoutsText.stopTimer())
        }
      }
    }
  }
}

@Composable
internal fun TimerCompletionBanner(message: String, onDismiss: () -> Unit) {
  Surface(
    Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = MaterialTheme.shapes.large
  ) {
    Row(
      Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Ic(Icons.Outlined.CheckCircle, size = 24.dp)
      Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
      IconButton(onClick = onDismiss) { Ic(BerillIcons.Close) }
    }
  }
}
