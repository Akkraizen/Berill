package dev.akkraizen.berill.plugins.habits.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.habits.data.HabitDef
import dev.akkraizen.berill.plugins.habits.data.HabitsData
import dev.akkraizen.berill.plugins.habits.data.UNITS
import dev.akkraizen.berill.plugins.habits.data.formatAmount
import dev.akkraizen.berill.plugins.habits.data.guessIconKey
import dev.akkraizen.berill.plugins.habits.data.unitTitle
import dev.akkraizen.berill.plugins.habits.i18n.HabitsText

@Composable
internal fun HabitsEditScreen(data: HabitsData) {
  val defs by remember(data) { data.defs.observe() }.collectAsState(emptyList())
  var editing by remember { mutableStateOf<Item<HabitDef>?>(null) }
  val sorted =
    defs.sortedWith(compareBy<Item<HabitDef>> { it.value.archived }.thenBy { it.value.order })

  PluginList {
    item { DetailHeader(HabitsText.habits(), BerillIcons.Edit) }
    sorted.forEachIndexed { index, h ->
      item(key = h.id) {
        Surface(
          onClick = { editing = h },
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.fillMaxWidth().animateItem()
        ) {
          Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Ic(habitIcon(h.id, h.value), size = 28.dp, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
              Text(
                h.value.label + if (h.value.archived) HabitsText.archivedSuffix() else "",
                style = MaterialTheme.typography.titleMedium
              )
              Text(
                listOfNotNull(
                  HabitsText.norm(formatAmount(h.value.unit, h.value.defaultDuration))(),
                  h.value.monthlyGoal?.let {
                    HabitsText.monthGoal(formatAmount(h.value.unit, it))()
                  }
                )
                  .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            IconButton(
              enabled = index > 0,
              onClick = { swapOrder(data, sorted, index, index - 1) }) {
              Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = HabitsText.moveUp())
            }
            IconButton(
              enabled = index < sorted.lastIndex,
              onClick = { swapOrder(data, sorted, index, index + 1) }) {
              Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = HabitsText.moveDown())
            }
          }
        }
      }
    }
  }

  editing?.let { h ->
    HabitDialog(
      initial = h.value,
      initialIcon = h.value.icon ?: guessIconKey(h.id),
      onDismiss = { editing = null },
      onSave = {
        data.defs.save(h.id, it)
        editing = null
      },
      onDelete = {
        data.defs.delete(h.id)
        editing = null
      }
    )
  }
}

private fun swapOrder(data: HabitsData, sorted: List<Item<HabitDef>>, i: Int, j: Int) {
  sorted.forEachIndexed { k, h ->
    val order = when (k) {
      i -> j
      j -> i
      else -> k
    }
    if (h.value.order != order) data.defs.save(h.id, h.value.copy(order = order))
  }
}

@Composable
internal fun HabitDialog(
  initial: HabitDef,
  initialIcon: String,
  onDismiss: () -> Unit,
  onSave: (HabitDef) -> Unit,
  onDelete: (() -> Unit)?
) {
  var label by remember { mutableStateOf(initial.label) }
  var iconKey by remember { mutableStateOf(initialIcon) }
  var unit by remember { mutableStateOf(initial.unit) }
  var norm by remember { mutableStateOf(initial.defaultDuration.toString()) }
  var goal by remember { mutableStateOf(initial.monthlyGoal?.toString() ?: "") }
  var archived by remember { mutableStateOf(initial.archived) }
  var askDelete by remember { mutableStateOf(false) }
  val confirm: () -> Unit = {
    onSave(
      initial.copy(
        label = label.trim(),
        icon = iconKey,
        unit = unit,
        defaultDuration = (norm.toIntOrNull() ?: 1).coerceAtLeast(1),
        monthlyGoal = goal.toIntOrNull(),
        archived = archived
      )
    )
  }
  DialogKeys(onConfirm = confirm, confirmEnabled = label.isNotBlank(), onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (onDelete == null) HabitsText.newHabit() else HabitsText.habit()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(
          label,
          { label = it },
          label = { Text(HabitsText.name()) },
          singleLine = true
        )
        IconPicker(iconKey) { iconKey = it }
        ChoiceChips(UNITS, unit, ::unitTitle, { unit = it })
        BerillTextField(
          norm,
          { norm = it.filter(Char::isDigit) },
          label = { Text(HabitsText.dailyNorm()) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        BerillTextField(
          goal,
          { goal = it.filter(Char::isDigit) },
          label = { Text(HabitsText.monthGoalOptional()) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (onDelete != null) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(HabitsText.archived(), modifier = Modifier.weight(1f))
            Switch(checked = archived, onCheckedChange = { archived = it })
          }
          TextButton(onClick = { askDelete = true }) {
            Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(HabitsText.deleteHabit(), color = MaterialTheme.colorScheme.error)
          }
        }
      }
    },
    confirmButton = {
      TextButton(enabled = label.isNotBlank(), onClick = confirm) { Text(HabitsText.save()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(HabitsText.cancel()) } }
  )
  if (askDelete && onDelete != null)
    ConfirmDialog(
      HabitsText.deleteHabitQuestion(),
      HabitsText.deleteHabitText(),
      onConfirm = onDelete,
      onDismiss = { askDelete = false }
    )
}

@Composable
private fun IconPicker(selected: String, onSelect: (String) -> Unit) {
  Text(
    HabitsText.icon(),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    BerillIcons.catalog.forEach { (key, vector) ->
      val isSelected = key == selected
      Surface(
        onClick = { onSelect(key) },
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected)
          MaterialTheme.colorScheme.primary
        else
          MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(40.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Ic(
            vector,
            tint = if (isSelected)
              MaterialTheme.colorScheme.onPrimary
            else
              MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}
