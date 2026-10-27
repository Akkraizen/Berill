package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.fields.DoubleField
import dev.akkraizen.berill.api.ui.fields.IntField
import dev.akkraizen.berill.api.ui.selection.pastelCheckboxColors
import dev.akkraizen.berill.plugins.workouts.data.LoggedSet
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText

@Composable
internal fun LoggedSetCard(
  number: Int,
  set: LoggedSet,
  singleDuration: Boolean,
  timerEnabled: Boolean,
  onChange: ((LoggedSet) -> LoggedSet) -> Unit,
  onTimer: () -> Unit,
  onRemove: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val checkboxColors = pastelCheckboxColors()
  val completedContainer = lerp(colors.surfaceContainer, checkboxColors.checkedBoxColor, 0.16f)
  val completedBorder = lerp(colors.outlineVariant, checkboxColors.checkedBoxColor, 0.3f)
  val container by animateColorAsState(
    if (set.done) completedContainer else colors.surfaceContainer,
    BerillMotion.effects(), label = "set-color")
  Surface(Modifier.fillMaxWidth(), color = container,
    contentColor = colors.onSurface,
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, if (set.done) completedBorder else colors.outlineVariant)) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (singleDuration) WorkoutsText.byTime() else WorkoutsText.setNumber(number)(),
          style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Checkbox(colors = checkboxColors, checked = set.done, onCheckedChange = { done -> onChange { it.copy(done = done) } })
        if (!singleDuration) IconButton(onClick = onRemove) {
          Ic(BerillIcons.Close, size = 18.dp)
        }
      }
      if (set.durationSeconds != null) {
        DurationField(set.durationSeconds, { value -> onChange { it.copy(durationSeconds = value) } })
        FilledTonalButton(onClick = onTimer, enabled = timerEnabled && !set.done) {
          Ic(Icons.Outlined.PlayArrow, size = 18.dp)
          Spacer(Modifier.width(6.dp))
          Text(WorkoutsText.start())
        }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DoubleField(set.weight, { value -> onChange { it.copy(weight = value?.coerceAtLeast(0.0)) } },
            WorkoutsText.weightKg(), Modifier.weight(1f))
          IntField(set.reps, { value -> onChange { it.copy(reps = value?.coerceAtLeast(0)) } },
            WorkoutsText.reps(), Modifier.weight(1f))
        }
      }
    }
  }
}
