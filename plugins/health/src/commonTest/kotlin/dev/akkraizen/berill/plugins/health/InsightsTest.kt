package dev.akkraizen.berill.plugins.health

import dev.akkraizen.berill.api.i18n.AppLanguage
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.plugins.health.data.InsightDay
import dev.akkraizen.berill.plugins.health.data.InsightFactor
import dev.akkraizen.berill.plugins.health.data.InsightMetric
import dev.akkraizen.berill.plugins.health.data.Strength
import dev.akkraizen.berill.plugins.health.data.insights
import dev.akkraizen.berill.plugins.health.data.pearson
import dev.akkraizen.berill.plugins.health.data.strength
import dev.akkraizen.berill.plugins.health.ui.insightSentence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsTest {
  @Test
  fun sleepAboveGoalRaisesEnergy() {
    val days = (0 until 10).map { i ->
      val rested = i % 2 == 0
      InsightDay(if (rested) 500 else 380, null, 4, 4, if (rested) 5 else 3)
    }
    val found = insights(days, sleepGoal = 480, stepsGoal = 8000)
    val energy = found.first { it.factor == InsightFactor.SLEEP && it.metric == InsightMetric.ENERGY }
    assertEquals(5.0, energy.above)
    assertEquals(3.0, energy.below)
    assertEquals(5, energy.aboveDays)
    assertEquals(Strength.STRONG, strength(energy.correlation))
    assertEquals(energy, found.first())
    assertTrue(found.none { it.factor == InsightFactor.STEPS })
  }

  @Test
  fun smallGroupsAreIgnored() {
    val days = listOf(InsightDay(500, 9000, 5, 5, 5), InsightDay(400, 3000, 2, 2, 2), InsightDay(500, 9000, 4, 4, 4))
    assertTrue(insights(days, 480, 8000).isEmpty())
  }

  @Test
  fun pearsonHandlesFlatSeries() {
    assertNull(pearson(listOf(1.0 to 2.0, 2.0 to 2.0, 3.0 to 2.0)))
    assertEquals(1.0, pearson(listOf(1.0 to 2.0, 2.0 to 4.0, 3.0 to 6.0))!!, 1e-9)
  }

  @Test
  fun sentenceIsLocalized() {
    val previous = I18n.language
    try {
      I18n.language = AppLanguage.fromCode("ru")!!
      val days = (0 until 8).map { i -> InsightDay(if (i < 4) 500 else 380, null, if (i < 4) 5 else 3, null, null) }
      val sentence = insightSentence(insights(days, 480, 8000).first())
      assertTrue(sentence.startsWith("В дни со сном от 8 ч"), sentence)
      assertTrue("5" in sentence && "3" in sentence, sentence)
    } finally {
      I18n.language = previous
    }
  }
}
