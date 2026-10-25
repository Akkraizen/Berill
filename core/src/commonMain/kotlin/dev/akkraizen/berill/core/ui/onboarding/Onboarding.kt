package dev.akkraizen.berill.core.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.EscapeAction
import dev.akkraizen.berill.api.ui.interaction.PrimaryAction
import dev.akkraizen.berill.api.ui.interaction.mouseScrollable
import dev.akkraizen.berill.core.BerillCore

private enum class Step {
  Welcome,
  Theme,
  Accent,
  Sections,
  Done
}

@Composable
internal fun Onboarding(core: BerillCore, onFinish: () -> Unit) {
  var step by rememberSaveable { mutableIntStateOf(0) }
  val steps = Step.entries
  val current = steps[step.coerceIn(0, steps.lastIndex)]

  PrimaryAction { if (current == Step.Done) onFinish() else step++ }
  EscapeAction(enabled = step > 0) { step = 0 }

  Surface(
    Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface
  ) {
    Box(Modifier.fillMaxSize()) {
      AnimatedBackdrop(Modifier.matchParentSize())
      Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
          Modifier.fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          StepIndicator(step, steps.size, Modifier.weight(1f))
          if (current != Step.Done)
            TextButton(onClick = onFinish) { Text(OnboardingText.skip()) }
        }
        AnimatedContent(
          targetState = step,
          transitionSpec = { BerillMotion.slide(forward = targetState > initialState) },
          modifier = Modifier.weight(1f).fillMaxWidth(),
          label = "onboarding-step"
        ) { s ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
              Modifier.widthIn(max = 760.dp)
                .fillMaxSize()
                .mouseScrollable(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
              when (steps[s.coerceIn(0, steps.lastIndex)]) {
                Step.Welcome -> WelcomeStep(core)
                Step.Theme -> ThemeStep(core)
                Step.Accent -> AccentStep(core)
                Step.Sections -> SectionsStep(core)
                Step.Done -> DoneStep(core)
              }
            }
          }
        }
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          AnimatedVisibility(visible = step > 0 && current != Step.Done) {
            TextButton(onClick = { step-- }) {
              Ic(BerillIcons.Back, size = 18.dp)
              Spacer(Modifier.width(6.dp))
              Text(OnboardingText.back())
            }
          }
          Spacer(Modifier.weight(1f))
          Button(onClick = { if (current == Step.Done) onFinish() else step++ }) {
            Text(
              when (current) {
                Step.Welcome -> OnboardingText.start()
                Step.Done -> OnboardingText.open()
                else -> OnboardingText.next()
              }
            )
            Spacer(Modifier.width(8.dp))
            Ic(
              if (current == Step.Done) Icons.Outlined.Check else BerillIcons.Forward,
              size = 18.dp
            )
          }
        }
      }
    }
  }
}
