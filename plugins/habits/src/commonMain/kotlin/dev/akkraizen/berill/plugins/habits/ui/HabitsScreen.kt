package dev.akkraizen.berill.plugins.habits.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.RawRecord
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.DAYS_SHORT
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.minusDays
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.weekdayIndex
import dev.akkraizen.berill.plugins.habits.HabitsPlugin
import dev.akkraizen.berill.plugins.habits.data.HabitDef
import dev.akkraizen.berill.plugins.habits.data.HabitsData
import dev.akkraizen.berill.plugins.habits.data.active
import dev.akkraizen.berill.plugins.habits.data.formatAmount
import dev.akkraizen.berill.plugins.habits.data.unitTitle
import dev.akkraizen.berill.plugins.habits.data.valueOf
import dev.akkraizen.berill.plugins.habits.i18n.HabitsText
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HabitsScreen(data: HabitsData) {
  val defs by remember(data) { data.defs.observe() }.collectAsState(emptyList())
  val days by remember(data) { data.days.observe() }.collectAsState(emptyList())
  val now = today()
  var offset by rememberSaveable { mutableStateOf(0) }
  var adding by remember { mutableStateOf(false) }
  var editingValue by remember { mutableStateOf<Item<HabitDef>?>(null) }

  val date = now.plusDays(offset)
  val byDate = days.associateBy { it.id }
  val record = byDate[date.toString()]
  val active = defs.active()
  val done = active.count { record.valueOf(it.id) >= it.value.defaultDuration }

  PluginList {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { offset-- }) {
          Icon(BerillIcons.Prev, contentDescription = HabitsText.previousDay())
        }
        Column(Modifier.weight(1f)) {
          Text(
            if (offset == 0)
              HabitsText.today()
            else
              "${DAYS_SHORT[date.weekdayIndex]}, ${date.dayMonth()}",
            style = MaterialTheme.typography.displaySmall
          )
          Text(
            HabitsText.doneOf(done, active.size)(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        if (offset != 0) TextButton(onClick = { offset = 0 }) { Text(HabitsText.today()) }
        IconButton(onClick = { offset++ }, enabled = offset < 0) {
          Icon(BerillIcons.Next, contentDescription = HabitsText.nextDay())
        }
      }
      Spacer(Modifier.height(8.dp))

      LinearWavyProgressIndicator(
        progress = { if (active.isEmpty()) 0f else done / active.size.toFloat() },
        modifier = Modifier.fillMaxWidth()
      )
    }
    item {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { data.ctx.nav.open(HabitsPlugin.STATS) }) {
          Ic(Icons.Outlined.Insights, size = 18.dp)
          Spacer(Modifier.width(8.dp))
          Text(HabitsText.statistics())
        }
        OutlinedButton(onClick = { data.ctx.nav.open(HabitsPlugin.EDIT) }) {
          Ic(BerillIcons.Edit, size = 18.dp)
          Spacer(Modifier.width(8.dp))
          Text(HabitsText.habits())
        }
      }
    }
    item {
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 320.dp).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          active.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              row.forEach { h ->
                val value = record.valueOf(h.id)
                Box(Modifier.weight(1f)) {
                  HabitCard(
                    h.value,
                    habitIcon(h.id, h.value),
                    value,
                    onToggle = { data.toggle(h.id, h.value, date, value) },
                    onLongPress = { editingValue = h }
                  )
                }
              }
              repeat(cols - row.size) { Box(Modifier.weight(1f)) }
            }
          }
        }
      }
    }
    item { WeekStrip(date, active, byDate) }
    item {
      if (active.isEmpty()) EmptyHint(HabitsText.noHabitsAddFirst())
      FilledTonalButton(onClick = { adding = true }) {
        Ic(BerillIcons.Add, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(HabitsText.habit())
      }
    }
  }

  editingValue?.let { h ->
    ValueDialog(
      def = h.value,
      icon = habitIcon(h.id, h.value),
      initial = record.valueOf(h.id),
      onDismiss = { editingValue = null },
      onSave = {
        data.setValue(h.id, date, it)
        editingValue = null
      }
    )
  }
  if (adding)
    HabitDialog(
      initial = HabitDef(defaultDuration = 20),
      initialIcon = "task",
      onDismiss = { adding = false },
      onSave = { def ->
        val base = def.label
          .trim()
          .lowercase()
          .replace(Regex("[^a-zа-яё0-9]+"), "_")
          .trim('_')
          .ifEmpty { "habit" }
        val taken = defs.map { it.id }.toSet()
        val id = if (base !in taken)
          base
        else
          generateSequence(2) { it + 1 }.map { "${base}_$it" }.first { it !in taken }
        data.defs.save(id, def.copy(order = (defs.maxOfOrNull { it.value.order } ?: 0) + 1))
        adding = false
      },
      onDelete = null
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun HabitsTodayCard(data: HabitsData) {
  val defs by remember(data) { data.defs.observe() }.collectAsState(emptyList())
  val days by remember(data) { data.days.observe() }.collectAsState(emptyList())
  val now = today()
  val record = days.firstOrNull { it.id == now.toString() }
  val active = defs.active()
  if (active.isEmpty()) {
    EmptyHint(HabitsText.noHabits())
    return
  }
  val done = active.count { record.valueOf(it.id) >= it.value.defaultDuration }
  Text(HabitsText.shortDoneOf(done, active.size)(), style = MaterialTheme.typography.headlineSmall)
  LinearWavyProgressIndicator(
    progress = { done / active.size.toFloat() },
    modifier = Modifier.fillMaxWidth()
  )
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    active.forEach { h ->
      val value = record.valueOf(h.id)
      BerillChip(
        selected = value >= h.value.defaultDuration,
        onClick = { data.toggle(h.id, h.value, now, value) },
        label = { Text(h.value.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Ic(habitIcon(h.id, h.value), size = 18.dp) }
      )
    }
  }
}

@Composable
private fun HabitCard(
  def: HabitDef,
  icon: ImageVector,
  value: Int,
  onToggle: () -> Unit,
  onLongPress: () -> Unit
) {
  val done = value >= def.defaultDuration

  val corner by
  animateDpAsState(
    if (done) 6.dp else 16.dp,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessLow
    )
  )
  val bg by
  animateColorAsState(
    if (done)
      MaterialTheme.colorScheme.primary
    else
      MaterialTheme.colorScheme.surfaceContainerHigh
  )
  val fg = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
  Surface(
    shape = RoundedCornerShape(corner),
    color = bg,
    modifier = Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(corner))
      .interactive(RoundedCornerShape(corner), onLongClick = onLongPress, onClick = onToggle)
  ) {
    Row(
      Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Ic(icon, size = 28.dp, tint = fg)
      Spacer(Modifier.size(14.dp))
      Column(Modifier.weight(1f)) {
        Text(def.label, style = MaterialTheme.typography.titleLarge, color = fg)
        Text(
          when {
            done -> HabitsText.cardDone(formatAmount(def.unit, value))()
            value > 0 ->
              HabitsText.cardProgress(
                formatAmount(def.unit, value),
                formatAmount(def.unit, def.defaultDuration)
              )()

            else -> HabitsText.cardNorm(formatAmount(def.unit, def.defaultDuration))()
          },
          color = fg.copy(alpha = 0.75f)
        )
      }
      if (done) Ic(BerillIcons.Check, size = 28.dp, tint = fg)
    }
  }
}

@Composable
private fun WeekStrip(
  end: LocalDate,
  habits: List<Item<HabitDef>>,
  byDate: Map<String, RawRecord>
) {
  val week = (6 downTo 0).map { end.minusDays(it) }
  Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(HabitsText.last7Days(), style = MaterialTheme.typography.titleMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        week.forEach { day ->
          val record = byDate[day.toString()]
          val done = habits.count { record.valueOf(it.id) >= it.value.defaultDuration }
          val ratio = if (habits.isEmpty()) 0f else done / habits.size.toFloat()
          Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (ratio == 0f)
                MaterialTheme.colorScheme.surfaceContainerHighest
              else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + 0.75f * ratio),
              modifier = Modifier.size(36.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text("$done", style = MaterialTheme.typography.labelLarge)
              }
            }
            Text(DAYS_SHORT[day.weekdayIndex], style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }
  }
}

@Composable
private fun ValueDialog(
  def: HabitDef,
  icon: ImageVector,
  initial: Int,
  onDismiss: () -> Unit,
  onSave: (Int) -> Unit
) {
  var text by remember { mutableStateOf(initial.toString()) }
  val confirm: () -> Unit = { onSave(text.toIntOrNull() ?: 0) }
  DialogKeys(onConfirm = confirm, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(def.label) },
    icon = { Icon(icon, contentDescription = null) },
    text = {
      BerillTextField(
        text,
        { text = it.filter(Char::isDigit).take(5) },
        label = { Text(HabitsText.valueLabel(unitTitle(def.unit), def.defaultDuration)(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
      )
    },
    confirmButton = { TextButton(onClick = confirm) { Text(HabitsText.save()) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(HabitsText.cancel()) } }
  )
}
