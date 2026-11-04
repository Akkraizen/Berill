package dev.akkraizen.berill.plugins.projects.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.layout.ListLayoutSetting
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.projects.data.BOARD_LAYOUT
import dev.akkraizen.berill.plugins.projects.data.MilestoneStyle
import dev.akkraizen.berill.plugins.projects.data.TASKS_LAYOUT
import dev.akkraizen.berill.plugins.projects.data.rememberMilestoneStyle
import dev.akkraizen.berill.plugins.projects.data.setMilestoneStyle
import dev.akkraizen.berill.plugins.projects.i18n.ProjectsText

@Composable
internal fun ProjectsSettings(ctx: PluginContext) {
  SectionCard(UiText.layoutChoose(), Icons.Outlined.GridView) {
    ListLayoutSetting(ctx, BOARD_LAYOUT, ProjectsText.settingBoard())
    ListLayoutSetting(ctx, TASKS_LAYOUT, ProjectsText.settingTasks())
    Text(ProjectsText.settingLayoutHint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
  SectionCard(ProjectsText.milestoneStyle(), Icons.Outlined.Flag) {
    ChoiceChips(MilestoneStyle.entries, rememberMilestoneStyle(ctx), ::milestoneStyleLabel, { setMilestoneStyle(ctx, it) }, icon = ::milestoneStyleIcon)
  }
}
