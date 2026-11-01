package dev.akkraizen.berill.plugins.languages

import dev.akkraizen.berill.plugins.languages.data.*
import kotlin.test.*

class SpacedRepetitionTest {
  @Test fun successfulReviewsIncreaseIntervalsAndFailureResetsThem() {
    val one = WordReview().answer(4, 0)
    assertEquals(1, one.intervalDays)
    val two = one.answer(4, one.dueAt)
    assertEquals(6, two.intervalDays)
    assertTrue(two.answer(4, two.dueAt).intervalDays > 6)
    assertEquals(0, two.answer(1, two.dueAt).repetitions)
    assertEquals(1, two.answer(1, two.dueAt).intervalDays)
  }
  @Test fun easeHasSafeFloorAndBadGradesAreRejected() {
    var review = WordReview()
    repeat(50) { review = review.answer(0, 0) }
    assertTrue(review.ease >= 1.3)
    assertFailsWith<IllegalArgumentException> { review.answer(6, 0) }
  }
}
