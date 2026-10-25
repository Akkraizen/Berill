package dev.akkraizen.berill.api.ui.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun AnimatedRoute(route: String?, modifier: Modifier = Modifier, content: @Composable (String?) -> Unit) {
  AnimatedContent(
    targetState = route,
    modifier = modifier,
    transitionSpec = {
      BerillMotion.slide(
        forward = targetState != null &&
          (initialState == null || targetState!!.length >= initialState!!.length)
      )
    },
    label = "route"
  ) { target ->
    content(target)
  }
}

@Composable
fun AppearAnimated(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val state = remember { MutableTransitionState(false).apply { targetState = true } }
  AnimatedVisibility(
    visibleState = state,
    modifier = modifier,
    enter = fadeIn(BerillMotion.effects()) +
      scaleIn(BerillMotion.fastSpatial(), initialScale = 0.96f),
    exit = fadeOut(BerillMotion.effects()),
    label = "appear"
  ) {
    content()
  }
}
