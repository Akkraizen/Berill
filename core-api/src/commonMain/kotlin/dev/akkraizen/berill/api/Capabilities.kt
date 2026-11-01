package dev.akkraizen.berill.api

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.reflect.KClass

interface Capability

interface CapabilityLookup {
  fun <T : Capability> all(type: KClass<T>): List<T>

  fun <T : Capability> observe(type: KClass<T>): Flow<List<T>> = kotlinx.coroutines.flow.flowOf(all(type))
}

inline fun <reified T : Capability> CapabilityLookup.all(): List<T> = all(T::class)

inline fun <reified T : Capability> CapabilityLookup.observe(): Flow<List<T>> = observe(T::class)

interface DeadlineSource : Capability {
  fun deadlines(): Flow<List<Deadline>>
}

data class Deadline(val title: String, val date: LocalDate, val pluginId: String, val route: String? = null)
