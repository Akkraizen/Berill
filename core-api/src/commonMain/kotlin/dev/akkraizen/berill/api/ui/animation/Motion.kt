package dev.akkraizen.berill.api.ui.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

object BerillMotion {
  fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

  fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

  fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

  fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)

  fun <T> slowEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f)

  private val enterFade = tween<Float>(durationMillis = 300, delayMillis = 90, easing = LinearOutSlowInEasing)
  private val exitFade = tween<Float>(durationMillis = 120, easing = FastOutLinearInEasing)

  private fun <T> axis(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 300f)

  fun slide(forward: Boolean): ContentTransform {
    val sign = if (forward) 1 else -1
    val enter: EnterTransition = slideInHorizontally(axis()) { sign * it / 5 } + fadeIn(enterFade)
    val exit: ExitTransition = slideOutHorizontally(axis()) { -sign * it / 5 } + fadeOut(exitFade)
    return ContentTransform(
      targetContentEnter = enter,
      initialContentExit = exit,
      targetContentZIndex = if (forward) 1f else 0f,
      sizeTransform = SizeTransform(clip = false)
    )
  }

  fun fadeThrough(): ContentTransform =
    ContentTransform(
      targetContentEnter = slideInVertically(axis()) { it / 12 } +
        scaleIn(axis(), initialScale = 0.96f) +
        fadeIn(enterFade),
      initialContentExit = slideOutVertically(axis()) { -it / 24 } + fadeOut(exitFade),
      targetContentZIndex = 1f,
      sizeTransform = SizeTransform(clip = false)
    )
}
