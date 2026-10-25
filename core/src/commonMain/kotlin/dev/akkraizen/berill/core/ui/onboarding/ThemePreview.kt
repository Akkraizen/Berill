package dev.akkraizen.berill.core.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.core.ui.theme.ThemeMode

@Composable
internal fun ThemeCard(
  mode: ThemeMode,
  accent: Color,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier
) {
  val colors = MaterialTheme.colorScheme
  val border by
  animateColorAsState(
    if (selected) colors.primary else colors.outlineVariant,
    BerillMotion.effects(),
    label = "theme-border"
  )
  val scale by
  animateFloatAsState(
    if (selected) 1f else 0.95f,
    BerillMotion.fastSpatial(),
    label = "theme-scale"
  )
  val shape = MaterialTheme.shapes.large
  Column(
    modifier
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(shape)
      .border(if (selected) 2.dp else 1.dp, border, shape)
      .interactive(shape, onClick = onClick)
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    val light: @Composable (Modifier) -> Unit = { m ->
      MiniApp(LIGHT_BG, LIGHT_CARD, LIGHT_LINE, accent, accent.copy(alpha = 0.25f), m)
    }
    val dark: @Composable (Modifier) -> Unit = { m ->
      MiniApp(DARK_BG, DARK_CARD, DARK_LINE, accent, accent.copy(alpha = 0.35f), m)
    }
    Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(MaterialTheme.shapes.medium)) {
      when (mode) {
        ThemeMode.Light -> light(Modifier.fillMaxSize())
        ThemeMode.Dark -> dark(Modifier.fillMaxSize())
        ThemeMode.System ->
          Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(0.dp))) {
              light(Modifier.fillMaxSize())
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { dark(Modifier.fillMaxSize()) }
          }
      }
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Ic(
        themeIcon(mode),
        size = 18.dp,
        tint = if (selected) colors.primary else colors.onSurfaceVariant
      )
      Text(
        mode.title,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

private fun themeIcon(mode: ThemeMode): ImageVector =
  when (mode) {
    ThemeMode.System -> Icons.Outlined.BrightnessAuto
    ThemeMode.Light -> Icons.Outlined.LightMode
    ThemeMode.Dark -> Icons.Outlined.DarkMode
  }

private val LIGHT_BG = Color(0xFFF7FBF1)

private val LIGHT_CARD = Color(0xFFE6E9E0)

private val LIGHT_LINE = Color(0xFFC2C9BD)

private val DARK_BG = Color(0xFF10140F)

private val DARK_CARD = Color(0xFF272B25)

private val DARK_LINE = Color(0xFF424940)

@Composable
internal fun MiniApp(
  background: Color,
  card: Color,
  line: Color,
  accent: Color,
  accentContainer: Color,
  modifier: Modifier
) {
  Row(
    modifier.background(background).padding(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Column(
      Modifier.width(18.dp).fillMaxHeight(),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box(
        Modifier.fillMaxWidth()
          .height(10.dp)
          .clip(CircleShape)
          .background(accentContainer)
      )
      repeat(4) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(line))
      }
    }
    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(Modifier.fillMaxWidth(0.55f).height(8.dp).clip(CircleShape).background(accent))
      Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
          Modifier.weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(card)
        )
        Box(
          Modifier.weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(card)
        )
      }
      Box(
        Modifier.fillMaxWidth()
          .weight(1f)
          .clip(RoundedCornerShape(6.dp))
          .background(card)
      ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(
            Modifier.fillMaxWidth(0.8f)
              .height(4.dp)
              .clip(CircleShape)
              .background(line)
          )
          Box(
            Modifier.fillMaxWidth(0.6f)
              .height(4.dp)
              .clip(CircleShape)
              .background(line)
          )
        }
      }
      Box(
        Modifier.fillMaxWidth(0.4f)
          .height(12.dp)
          .clip(CircleShape)
          .background(accent)
          .align(Alignment.End)
      )
    }
  }
}
