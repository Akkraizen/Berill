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

enum class TodayKind { TASK, DEADLINE, EVENT, WORKOUT, DOSE, HABIT }

data class TodayItem(
  val id: String,
  val title: String,
  val kind: TodayKind,
  val pluginId: String,
  val detail: String? = null,
  val time: String? = null,
  val due: LocalDate? = null,
  val done: Boolean? = null,
  val overdue: Boolean = false,
  val toggle: (() -> Unit)? = null,
  val open: () -> Unit
)

interface TodaySource : Capability {
  fun today(date: LocalDate): Flow<List<TodayItem>>
}
