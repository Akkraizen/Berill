package dev.akkraizen.berill.api.ui.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.color

@Composable
fun StatTiles(stats: List<Stat>, minTileWidth: Dp = 160.dp) {
  if (stats.isEmpty()) return
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val cols = (maxWidth / minTileWidth).toInt().coerceIn(1, minOf(4, stats.size))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      stats.chunked(cols).forEach { row ->
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          row.forEach { StatTile(it, Modifier.weight(1f).fillMaxHeight()) }
          repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
      }
    }
  }
}

@Composable
fun StatTile(stat: Stat, modifier: Modifier = Modifier) {
  Surface(
    modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer
  ) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (stat.icon != null)
          Ic(stat.icon, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          stat.label,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      AnimatedContent(
        targetState = stat.value,
        transitionSpec = {
          val up = (targetState.filter(Char::isDigit).toLongOrNull() ?: 0L) >=
            (initialState.filter(Char::isDigit).toLongOrNull() ?: 0L)
          (
            slideInVertically(BerillMotion.spatial()) { if (up) it else -it } +
              fadeIn(BerillMotion.effects())
            ) togetherWith
            (
              slideOutVertically(BerillMotion.spatial()) { if (up) -it else it } +
                fadeOut(BerillMotion.effects())
              )
        },
        label = "stat"
      ) { value ->
        Text(
          value,
          style = MaterialTheme.typography.headlineSmall,
          color = stat.tone.color(),
          fontWeight = FontWeight.SemiBold
        )
      }
      if (stat.sub != null)
        Text(
          stat.sub,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2
        )
    }
  }
}
