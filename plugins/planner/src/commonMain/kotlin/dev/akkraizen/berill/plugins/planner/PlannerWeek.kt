package dev.akkraizen.berill.plugins.planner

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.state.rememberDraft

@Composable
internal fun GoalsCard(data: PlannerData, weekId: String, goals: List<Item<PlannerTask>>) {
  var input by rememberDraft("planner/goal/$weekId")
  SectionCard(PlannerText.weekGoals(), Icons.Outlined.Flag) {
    if (goals.isNotEmpty()) ProgressLine(goals.count { it.value.done } / goals.size.toFloat())
    if (goals.isEmpty()) EmptyHint(PlannerText.noGoals())
    goals.forEach { item ->
      TaskRow(
        item.value,
        compact = false,
        onToggle = { data.edit(item.id) { it.copy(done = !it.done) } },
        onNextDay = null,
        onDelete = { data.tasks.delete(item.id) }
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      BerillTextField(
        input,
        { input = it },
        placeholder = { Text(PlannerText.weekGoal()) },
        singleLine = true,
        modifier = Modifier.weight(1f),
        onSubmit = {
          if (input.isNotBlank()) {
            data.addGoal(weekId, input)
            input = ""
          }
        }
      )
      TextButton(
        enabled = input.isNotBlank(),
        onClick = {
          data.addGoal(weekId, input)
          input = ""
        }
      ) {
        Icon(BerillIcons.Add, contentDescription = PlannerText.addGoal())
      }
    }
  }
}

@Composable
internal fun WeekSummary(data: PlannerData, weekId: String) {
  val notes by
  remember(data, weekId) { data.weeks.observe(weekId) }.collectAsState(data.weeks.get(weekId))
  SectionCard(PlannerText.weekSummary(), Icons.Outlined.Description) {
    key(weekId) {
      CommitTextField(
        notes?.summary ?: "",
        { v -> data.weeks.save(weekId, WeekNotes(v)) },
        PlannerText.weekSummaryHint(),
        singleLine = false
      )
    }
  }
}
