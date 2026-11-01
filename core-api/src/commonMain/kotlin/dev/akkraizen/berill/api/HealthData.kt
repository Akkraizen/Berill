package dev.akkraizen.berill.api

import kotlinx.datetime.LocalDate

@kotlinx.serialization.Serializable
data class HealthDay(val date: String, val steps: Long? = null, val sleepMinutes: Long? = null, val weightKg: Double? = null)
interface HealthDataSink : Capability { fun accept(day: HealthDay) }
interface HealthSummarySource : Capability { fun day(date: LocalDate): HealthDay? }
