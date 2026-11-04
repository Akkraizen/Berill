package dev.akkraizen.berill.plugins.projects.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.layout.ListLayout
import dev.akkraizen.berill.api.ui.layout.ListLayoutKey
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

val BOARD_LAYOUT = ListLayoutKey("boardLayout", listOf(ListLayout.BOARD, ListLayout.CARDS, ListLayout.ROWS), ListLayout.BOARD)
val TASKS_LAYOUT = ListLayoutKey("tasksLayout", listOf(ListLayout.ROWS, ListLayout.CARDS, ListLayout.BOARD), ListLayout.ROWS)

enum class MilestoneStyle { TIMELINE, ROADMAP, CARDS }

private const val MILESTONE_STYLE = "milestoneStyle"

@Composable
internal fun rememberMilestoneStyle(ctx: PluginContext): MilestoneStyle {
  val raw by remember(ctx) { ctx.settings.observe(MILESTONE_STYLE) }.collectAsState(ctx.settings.get(MILESTONE_STYLE))
  val name = (raw as? JsonPrimitive)?.contentOrNull
  return MilestoneStyle.entries.firstOrNull { it.name == name } ?: MilestoneStyle.TIMELINE
}

internal fun setMilestoneStyle(ctx: PluginContext, style: MilestoneStyle) = ctx.settings.set(MILESTONE_STYLE, JsonPrimitive(style.name))
