package dev.akkraizen.berill.plugins.languages.data

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.plugins.languages.LanguagesPlugin
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class WordReview(val repetitions: Int = 0, val intervalDays: Int = 0, val ease: Double = 2.5, val dueAt: Long = 0)

fun WordReview.answer(quality: Int, now: Long): WordReview {
  require(quality in 0..5)
  val nextEase = (ease + 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)).coerceAtLeast(1.3)
  if (quality < 3) return WordReview(0, 1, nextEase, now + 86400000L)
  val interval = when (repetitions) { 0 -> 1; 1 -> 6; else -> (intervalDays * ease).roundToInt().coerceAtLeast(1) }
  return WordReview(repetitions + 1, interval, nextEase, now + interval * 86400000L)
}

internal fun reviews(ctx: PluginContext) = ctx.data.collection(LanguagesPlugin.REVIEWS, WordReview.serializer())
