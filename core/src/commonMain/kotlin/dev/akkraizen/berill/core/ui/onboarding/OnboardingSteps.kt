package dev.akkraizen.berill.core.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.AppearAnimated
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.ui.brand.MosaicLogo
import dev.akkraizen.berill.core.ui.settings.LanguageChips
import dev.akkraizen.berill.core.ui.theme.ThemeMode
import kotlinx.coroutines.delay

@Composable
internal fun WelcomeStep(core: BerillCore) {
  val appearance by core.appearance.state.collectAsState()
  Column(
    Modifier.fillMaxWidth().padding(top = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
    MosaicLogo(156.dp)
    AppearAnimated {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          OnboardingText.welcomeTitle(),
          style = MaterialTheme.typography.headlineMedium,
          textAlign = TextAlign.Center
        )
        Text(
          OnboardingText.welcomeText(),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.widthIn(max = 520.dp)
        )
      }
    }
    Text(
      OnboardingText.language(),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LanguageChips(core, appearance, OnboardingText.system())
  }
}

@Composable
internal fun ThemeStep(core: BerillCore) {
  val appearance by core.appearance.state.collectAsState()
  StepHeader(OnboardingText.themeTitle(), OnboardingText.themeText())
  val accent = MaterialTheme.colorScheme.primary
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    ThemeMode.entries.forEach { mode ->
      ThemeCard(
        mode = mode,
        accent = accent,
        selected = appearance.mode == mode,
        onClick = { core.appearance.setMode(mode) },
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
internal fun AccentStep(core: BerillCore) {
  val appearance by core.appearance.state.collectAsState()
  StepHeader(OnboardingText.accentTitle(), OnboardingText.accentText())

  val colors = MaterialTheme.colorScheme
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    MiniApp(
      background = colors.surface,
      card = colors.surfaceContainerHigh,
      line = colors.outlineVariant,
      accent = colors.primary,
      accentContainer = colors.primaryContainer,
      modifier = Modifier.widthIn(max = 420.dp)
        .fillMaxWidth()
        .aspectRatio(16f / 10f)
        .clip(MaterialTheme.shapes.large)
        .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.large)
    )
  }
  AccentSwatches(appearance.accent, core.appearance::setAccent)
}

@Composable
internal fun SectionsStep(core: BerillCore) {
  val enabled by core.plugins.enabled.collectAsState()
  val all = core.plugins.all
  StepHeader(OnboardingText.sectionsTitle(), OnboardingText.sectionsText())
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Text(
      OnboardingText.enabledCount(enabled.size),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.weight(1f)
    )
    TextButton(onClick = { all.forEach { core.plugins.install(it.id) } }) {
      Text(OnboardingText.all())
    }
    TextButton(onClick = { all.forEach { core.plugins.setEnabled(it.id, false) } }) {
      Text(OnboardingText.none())
    }
  }
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val cols = (maxWidth / 240.dp).toInt().coerceIn(1, 3)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      all.chunked(cols).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          row.forEach { p ->
            val on = enabled.any { it.id == p.id }
            Box(Modifier.weight(1f)) {
              AppearAnimated {
                PluginTile(p, on) {
                  if (on) core.plugins.setEnabled(p.id, false) else core.plugins.install(p.id)
                }
              }
            }
          }
          repeat(cols - row.size) { Box(Modifier.weight(1f)) }
        }
      }
    }
  }
}

@Composable
internal fun DoneStep(core: BerillCore) {
  val enabled by core.plugins.enabled.collectAsState()
  Column(
    Modifier.fillMaxWidth().padding(top = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
    MosaicLogo(112.dp)
    AppearAnimated {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          OnboardingText.doneTitle(),
          style = MaterialTheme.typography.headlineMedium,
          textAlign = TextAlign.Center
        )
        Text(
          OnboardingText.doneText(),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.widthIn(max = 520.dp)
        )
      }
    }
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      enabled.forEachIndexed { i, p ->
        val shown = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
          delay(60L * i)
          shown.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f))
        }
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.graphicsLayer {
            val v = shown.value
            scaleX = v
            scaleY = v
            alpha = v.coerceIn(0f, 1f)
          }
        ) {
          Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Ic(p.icon, size = 18.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
              p.title,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
          }
        }
      }
    }
  }
}
