package dev.akkraizen.berill.api.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.display.Ic

private val MinTitleWidth = 200.dp
private val Gap = 12.dp
private val StackGap = 8.dp

@Composable
fun DetailHeader(
  title: String,
  icon: ImageVector? = null,
  trailing: (@Composable () -> Unit)? = null
) {
  Layout(
    modifier = Modifier.fillMaxWidth(),
    content = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (icon != null) Ic(icon, size = 28.dp, tint = MaterialTheme.colorScheme.primary)
        Text(
          title,
          style = MaterialTheme.typography.headlineSmall,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis
        )
      }
      Box { trailing?.invoke() }
    }
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val loose = Constraints(maxWidth = width)
    val action = measurables[1].measure(loose)
    val gap = Gap.roundToPx()
    val inline = action.width == 0 || width - action.width - gap >= MinTitleWidth.roundToPx()
    if (inline) {
      val headWidth = if (action.width == 0) width else width - action.width - gap
      val head = measurables[0].measure(Constraints(maxWidth = headWidth))
      val height = maxOf(head.height, action.height)
      layout(width, height) {
        head.placeRelative(0, (height - head.height) / 2)
        action.placeRelative(width - action.width, (height - action.height) / 2)
      }
    } else {
      val head = measurables[0].measure(loose)
      val stackGap = StackGap.roundToPx()
      layout(width, head.height + stackGap + action.height) {
        head.placeRelative(0, 0)
        action.placeRelative(0, head.height + stackGap)
      }
    }
  }
}
