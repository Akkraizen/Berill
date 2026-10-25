package dev.akkraizen.berill.api.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TwoPane(
  left: @Composable ColumnScope.() -> Unit,
  right: @Composable ColumnScope.() -> Unit,
  breakpoint: Dp = 840.dp,
  leftWeight: Float = 1.4f
) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    if (maxWidth >= breakpoint) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
          Modifier.weight(leftWeight),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          content = left
        )
        Column(
          Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          content = right
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        left()
        right()
      }
    }
  }
}
