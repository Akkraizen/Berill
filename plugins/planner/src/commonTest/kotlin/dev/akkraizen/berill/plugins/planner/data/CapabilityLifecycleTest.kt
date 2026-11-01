package dev.akkraizen.berill.plugins.planner.data

import dev.akkraizen.berill.api.*
import kotlin.reflect.KClass
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate

class CapabilityLifecycleTest {
  @Test fun existingPlannerSubscriptionDropsDisabledSourceAndRestoresItWhenReenabled(): Unit = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
      val data = MutableStateFlow(listOf(Deadline("First", LocalDate(2026, 10, 30), "study")))
      val source = object : DeadlineSource { override fun deadlines(): Flow<List<Deadline>> = data }
      val active = MutableStateFlow<List<DeadlineSource>>(listOf(source))
      val lookup = object : CapabilityLookup {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Capability> all(type: KClass<T>): List<T> = active.value.filter { type.isInstance(it) }.map { it as T }
        @Suppress("UNCHECKED_CAST")
        override fun <T : Capability> observe(type: KClass<T>): Flow<List<T>> = active.map { list -> list.filter { type.isInstance(it) }.map { it as T } }
      }
      val http = object : HttpAccess {
        override suspend fun getJson(url: String, headers: Map<String, String>): kotlinx.serialization.json.JsonElement = error("unused")
        override suspend fun send(request: HttpRequest): HttpResponse = error("unused")
      }
      val base = MemoryContext("planner", http)
      val ctx = object : PluginContext by base { override val capabilities = lookup }
      val observed = PlannerData(ctx).deadlines().stateIn(scope, SharingStarted.Eagerly, emptyList())
      withTimeout(5000) { observed.first { it.singleOrNull()?.title == "First" } }
      active.value = emptyList()
      withTimeout(5000) { observed.first { it.isEmpty() } }
      data.value = listOf(Deadline("Updated while disabled", LocalDate(2026, 10, 31), "study"))
      active.value = listOf(source)
      withTimeout(5000) { observed.first { it.singleOrNull()?.title == "Updated while disabled" } }
      ctx.settings.set(SHOW_DEADLINES, false)
      withTimeout(5000) { observed.first { it.isEmpty() } }
      active.value = emptyList()
      active.value = listOf(source)
      assertFalse(ctx.settings.boolean(SHOW_DEADLINES, true))
      ctx.settings.set(SHOW_DEADLINES, true)
      withTimeout(5000) { observed.first { it.singleOrNull()?.title == "Updated while disabled" } }
    } finally { scope.cancel() }
  }
}
