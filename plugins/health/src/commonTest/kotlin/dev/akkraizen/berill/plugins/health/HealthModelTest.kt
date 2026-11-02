package dev.akkraizen.berill.plugins.health

import dev.akkraizen.berill.plugins.health.data.Intake
import dev.akkraizen.berill.plugins.health.data.IntakeStatus
import dev.akkraizen.berill.plugins.health.data.Medication
import dev.akkraizen.berill.plugins.health.data.SleepEntry
import dev.akkraizen.berill.plugins.health.data.Symptom
import dev.akkraizen.berill.plugins.health.data.VitalEntry
import dev.akkraizen.berill.plugins.health.data.VitalType
import dev.akkraizen.berill.plugins.health.data.WellbeingDay
import dev.akkraizen.berill.plugins.health.data.dosesOn
import dev.akkraizen.berill.plugins.health.data.intakeId
import dev.akkraizen.berill.plugins.health.i18n.HealthText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class HealthModelTest {
  private val day = LocalDate(2026, 11, 2)

  @Test
  fun scheduleRespectsCourseIntervalAndPause() {
    val course = Medication("Vitamin D", start = "2026-11-01", end = "2026-11-10", everyDays = 2)
    assertTrue(course.isScheduledOn(LocalDate(2026, 11, 1)))
    assertFalse(course.isScheduledOn(day))
    assertTrue(course.isScheduledOn(LocalDate(2026, 11, 3)))
    assertFalse(course.isScheduledOn(LocalDate(2026, 11, 11)))
    assertFalse(course.isScheduledOn(LocalDate(2026, 10, 31)))
    assertFalse(Medication("Paused", active = false).isScheduledOn(day))
  }

  @Test
  fun dosesAreSortedAndMatchedWithIntakes() {
    val meds = listOf(
      "a" to Medication("Magnesium", times = listOf("21:00", "9:00", "bad")),
      "b" to Medication("Omega-3"),
      "c" to Medication("Old", end = "2026-11-01")
    )
    val taken = mapOf(intakeId("a", day.toString(), "09:00") to Intake("a", day.toString(), "09:00", IntakeStatus.TAKEN, 1))
    val doses = dosesOn(day, meds, taken)
    assertEquals(listOf("09:00", "21:00", null), doses.map { it.time })
    assertEquals(IntakeStatus.TAKEN, doses.first().intake?.status)
    assertNull(doses[1].intake)
    assertEquals("b|2026-11-02|any", doses.last().id)
  }

  @Test
  fun vitalsAreValidatedByType() {
    assertNull(VitalEntry(VitalType.PULSE, "2026-11-02", "08:30", 64.0).problem())
    assertNotNull(VitalEntry(VitalType.PULSE, "2026-11-02", value = 400.0).problem())
    assertNull(VitalEntry(VitalType.BLOOD_PRESSURE, "2026-11-02", value = 120.0, value2 = 80.0).problem())
    assertNotNull(VitalEntry(VitalType.BLOOD_PRESSURE, "2026-11-02", value = 120.0).problem())
    assertNotNull(VitalEntry(VitalType.BLOOD_PRESSURE, "2026-11-02", value = 80.0, value2 = 120.0).problem())
    assertNotNull(VitalEntry(VitalType.TEMPERATURE, "2026-11-02", value = 36.6, value2 = 1.0).problem())
    assertNotNull(VitalEntry(VitalType.OXYGEN, "yesterday", value = 98.0).problem())
    assertNotNull(VitalEntry(VitalType.GLUCOSE, "2026-11-02", "25:00", 5.5).problem())
  }

  @Test
  fun sleepDurationCrossesMidnight() {
    assertEquals(450, SleepEntry("2026-11-02", "23:30", "07:00").minutes())
    assertEquals(90, SleepEntry("2026-11-02", "13:00", "14:30").minutes())
    assertNull(SleepEntry("2026-11-02", "23:00", "23:00").minutes())
    assertNotNull(SleepEntry("2026-11-02", "23:00", "07:00", quality = 6).problem())
    assertNull(SleepEntry("2026-11-02", "23:00", "07:00", quality = 4).problem())
  }

  @Test
  fun wellbeingRatingsAndSymptomsAreChecked() {
    assertTrue(WellbeingDay("2026-11-02").isEmpty)
    assertNull(WellbeingDay("2026-11-02", wellbeing = 4, symptoms = listOf(Symptom("Headache", 2))).problem())
    assertNotNull(WellbeingDay("2026-11-02", mood = 0).problem())
    assertNotNull(WellbeingDay("2026-11-02", symptoms = listOf(Symptom(" ", 1))).problem())
    assertNotNull(WellbeingDay("2026-11-02", symptoms = listOf(Symptom("Cough", 4))).problem())
  }

  @Test
  fun catalogHasBothLanguages() {
    assertEquals("Здоровье", HealthText.title.get("ru"))
    assertEquals("Health", HealthText.title.get("en"))
    assertEquals("Принято 1 из 3", HealthText.dosesDone(1, 3).get("ru"))
    assertEquals("Time to take Magnesium", HealthText.reminderTitle("Magnesium").get("en"))
  }
}
