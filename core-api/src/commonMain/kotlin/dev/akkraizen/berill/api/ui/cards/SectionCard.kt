package dev.akkraizen.berill.api.ui.cards

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.interaction.interactive

@Composable
fun SectionCard(
  title: String? = null,
  icon: ImageVector? = null,
  modifier: Modifier = Modifier,
  onTitleClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  Surface(
    modifier.fillMaxWidth().let {
      if (onTitleClick != null)
        it.interactive(MaterialTheme.shapes.extraLarge, onClick = onTitleClick)
      else
        it
    },
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh
  ) {
    Column(
      Modifier.animateContentSize(BerillMotion.spatial()).padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (title != null || trailing != null) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (icon != null) Ic(icon, tint = MaterialTheme.colorScheme.primary)
          Text(
            title ?: "",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
          )
          trailing?.invoke()
        }
      }
      content()
    }
  }
}
