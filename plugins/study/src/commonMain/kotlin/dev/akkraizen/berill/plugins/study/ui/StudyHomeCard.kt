package dev.akkraizen.berill.plugins.study.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.deadlineInfo
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.study.StudyPlugin
import dev.akkraizen.berill.plugins.study.data.DOING
import dev.akkraizen.berill.plugins.study.data.SUBMITTED
import dev.akkraizen.berill.plugins.study.data.activeSorted
import dev.akkraizen.berill.plugins.study.data.tasks
import dev.akkraizen.berill.plugins.study.i18n.StudyText

fun statusIcon(status: String): ImageVector =
  when (status) {
    DOING -> Icons.Outlined.Pending
    SUBMITTED -> Icons.Outlined.CheckCircle
    else -> Icons.Outlined.RadioButtonUnchecked
  }

@Composable
internal fun NearestDeadlines(ctx: PluginContext) {
  val all by remember(ctx) { tasks(ctx).observe() }.collectAsState(emptyList())
  val nearest = activeSorted(all).take(5)
  if (nearest.isEmpty()) EmptyHint(StudyText.noDeadlines())
  nearest.forEach { item ->
    val info = deadlineInfo(item.value.deadline.toDateOrNull())
    Row(
      Modifier.fillMaxWidth()
        .interactive(bleed = 6.dp) {
          ctx.nav.openPlugin(StudyPlugin.id, StudyPlugin.TASK_ROUTE + item.id)
        }
        .padding(vertical = 4.dp)
    ) {
      Text(item.value.fullTitle, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(info.label, color = info.tone.color(), style = MaterialTheme.typography.labelLarge)
    }
  }
}
