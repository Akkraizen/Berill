package dev.akkraizen.berill.plugins.planner.data

import dev.akkraizen.berill.api.Capability
import dev.akkraizen.berill.api.CapabilityLookup
import dev.akkraizen.berill.api.DataAccess
import dev.akkraizen.berill.api.DataCollection
import dev.akkraizen.berill.api.FileAccess
import dev.akkraizen.berill.api.HttpAccess
import dev.akkraizen.berill.api.HttpRequest
import dev.akkraizen.berill.api.HttpResponse
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.Navigator
import dev.akkraizen.berill.api.NoFileAccess
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginSettings
import dev.akkraizen.berill.api.RawCollection
import dev.akkraizen.berill.api.SecretStore
import dev.akkraizen.berill.api.str
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.planner.PlannerPlugin
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private class MemoryCollection<T>(override val name: String) : DataCollection<T> {
  val items = MutableStateFlow<Map<String, T>>(emptyMap())

  override fun observe(): Flow<List<Item<T>>> = items.map { m -> m.map { Item(it.key, it.value) } }

  override fun observe(id: String): Flow<T?> = items.map { it[id] }

  override fun get(id: String): T? = items.value[id]

  override fun all(): List<Item<T>> = items.value.map { Item(it.key, it.value) }

  override fun save(id: String, value: T) {
    items.value = items.value + (id to value)
  }

  override fun delete(id: String) {
    items.value = items.value - id
  }
}

private class MemoryContext(override val pluginId: String, override val http: HttpAccess) : PluginContext {
  private val collections = HashMap<String, MemoryCollection<*>>()
  private val values = MutableStateFlow<Map<String, JsonElement>>(emptyMap())
  private val secretValues = MutableStateFlow<Map<String, String>>(emptyMap())

  override val data: DataAccess = object : DataAccess {
    @Suppress("UNCHECKED_CAST")
    override fun <T> collection(name: String, serializer: KSerializer<T>): DataCollection<T> =
      collections.getOrPut(name) { MemoryCollection<T>(name) } as DataCollection<T>

    override fun raw(name: String): RawCollection = error("unused")

    override fun newId(): String = Uuid.random().toString()
  }

  override val nav: Navigator = object : Navigator {
    override val route: StateFlow<String?> = MutableStateFlow(null)
    override val page: StateFlow<String?> = MutableStateFlow(null)

    override fun openPage(pageId: String) = Unit

    override fun open(route: String?) = Unit

    override fun back() = false

    override fun openPlugin(pluginId: String, route: String?, page: String?) = Unit

    override fun openNote(noteId: String?) = Unit

    override fun openSettings() = Unit
  }

  override val capabilities: CapabilityLookup = object : CapabilityLookup {
    override fun <T : Capability> all(type: KClass<T>): List<T> = emptyList()
  }

  override val settings: PluginSettings = object : PluginSettings {
    override fun get(key: String): JsonElement? = values.value[key]

    override fun observe(key: String): Flow<JsonElement?> = values.map { it[key] }

    override fun set(key: String, value: JsonElement) {
      values.value = values.value + (key to value)
    }
  }

  override val files: FileAccess = NoFileAccess

  override val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)

  override val secrets: SecretStore = object : SecretStore {
    override fun get(key: String): String? = secretValues.value[key]

    override fun set(key: String, value: String?) {
      secretValues.value = if (value == null) secretValues.value - key else secretValues.value + (key to value)
    }

    override fun observe(key: String): Flow<String?> = secretValues.map { it[key] }
  }
}

private class FakeGoogle : HttpAccess {
  val events = LinkedHashMap<String, JsonObject>()
  var version = 0
  val calls = ArrayList<String>()

  override suspend fun getJson(url: String, headers: Map<String, String>): JsonElement = error("unused")

  private fun respond(status: Int, body: JsonElement? = null) = HttpResponse(status, body?.toString() ?: "")

  private fun stamp(source: JsonObject, id: String): JsonObject {
    version++
    return JsonObject(source + mapOf("id" to JsonPrimitive(id), "etag" to JsonPrimitive("\"v$version\"")))
  }

  fun putRemote(id: String, title: String, date: String, start: String, end: String) {
    events[id] = stamp(
      buildJsonObject {
        put("summary", title)
        put("start", buildJsonObject { put("dateTime", "${date}T$start:00Z") })
        put("end", buildJsonObject { put("dateTime", "${date}T$end:00Z") })
      },
      id
    )
  }

  override suspend fun send(request: HttpRequest): HttpResponse {
    val path = request.url.substringBefore('?')
    calls += "${request.method} ${path.substringAfter("/calendar/v3")}"
    if (path.endsWith("/token"))
      return respond(200, buildJsonObject { put("access_token", "a"); put("expires_in", 3600) })
    if (path.endsWith("/users/me/calendarList"))
      return respond(
        200,
        buildJsonObject {
          put("items", JsonArray(listOf(buildJsonObject { put("id", "me@x"); put("summary", "Me"); put("primary", true); put("accessRole", "owner") })))
        }
      )
    val eventsPath = "/calendars/me%40x/events"
    require(path.contains(eventsPath)) { path }
    val id = path.substringAfter(eventsPath).removePrefix("/").takeIf { it.isNotEmpty() }
    val body = request.body?.let { Json.parseToJsonElement(it).jsonObject }
    return when (request.method) {
      "GET" ->
        if (id == null) respond(200, buildJsonObject { put("items", JsonArray(events.values.toList())) })
        else events[id]?.let { respond(200, it) } ?: respond(404)
      "POST" -> {
        val newId = body!!.str("id") ?: "remote${events.size}"
        if (newId in events) return respond(409)
        events[newId] = stamp(body, newId)
        respond(200, events[newId])
      }
      "PATCH" -> {
        val current = events[id!!] ?: return respond(404)
        val match = request.headers["If-Match"]
        if (match != null && match != current.str("etag")) return respond(412)
        events[id] = stamp(JsonObject(current + body!!), id)
        respond(200, events[id])
      }
      "DELETE" -> if (events.remove(id) != null) respond(204) else respond(410)
      else -> respond(405)
    }
  }
}

class GoogleCalendarTest {
  private val utc = TimeZone.UTC

  private fun setup(name: String): Triple<MemoryContext, FakeGoogle, GoogleCalendarSync> {
    val google = FakeGoogle()
    val ctx = MemoryContext(name, google)
    ctx.secrets.set("google.refresh", "r")
    return Triple(ctx, google, GoogleCalendarSync.of(ctx))
  }

  private fun events(ctx: PluginContext) = ctx.data.collection(PlannerPlugin.EVENTS, CalendarEvent.serializer())

  @Test
  fun pushesNewLocalEventsWithStableIdsAndImportsRemoteOnes(): Unit = runBlocking {
    val (ctx, google, sync) = setup("push")
    val day = today().toString()
    val id = CalendarData(ctx).save(null, CalendarEvent("Встреча", day, start = "10:00", end = "11:00"))
    google.putRemote("abc123", "Из Google", day, "12:00", "13:00")
    sync.sync()
    val ownGoogleId = googleEventIdFor(id)!!
    assertTrue(ownGoogleId in google.events)
    assertEquals("Встреча", google.events.getValue(ownGoogleId).str("summary"))
    val local = events(ctx).get(id)!!
    assertEquals(ownGoogleId, local.googleId)
    assertEquals("me@x", local.calendarId)
    assertEquals(false, local.dirty)
    val imported = events(ctx).get(docIdForGoogle("me@x", "abc123"))
    assertNotNull(imported)
    assertEquals("Из Google", imported.title)
    assertEquals(2, events(ctx).all().size)
  }

  @Test
  fun editsAndDeletesTravelBothWays(): Unit = runBlocking {
    val (ctx, google, sync) = setup("edits")
    val day = today().toString()
    val data = CalendarData(ctx)
    val id = data.save(null, CalendarEvent("Старое", day, start = "10:00", end = "11:00"))
    google.putRemote("other", "Чужое", day, "15:00", "16:00")
    sync.sync()
    data.save(id, events(ctx).get(id)!!.copy(title = "Новое"))
    sync.sync()
    assertEquals("Новое", google.events.getValue(googleEventIdFor(id)!!).str("summary"))
    data.delete(id)
    sync.sync()
    assertNull(google.events[googleEventIdFor(id)!!])
    assertNull(events(ctx).get(id))
    google.events.remove("other")
    sync.sync()
    assertTrue(events(ctx).all().isEmpty())
  }

  @Test
  fun remoteChangeWinsOverStaleLocalEdit(): Unit = runBlocking {
    val (ctx, google, sync) = setup("conflict")
    val day = today().toString()
    google.putRemote("shared", "Версия 1", day, "09:00", "10:00")
    sync.sync()
    val localId = docIdForGoogle("me@x", "shared")
    val current = google.events.getValue("shared")
    google.events["shared"] = JsonObject(current + mapOf("summary" to JsonPrimitive("Версия 2"), "etag" to JsonPrimitive("\"remote\"")))
    CalendarData(ctx).save(localId, events(ctx).get(localId)!!.copy(title = "Локально"))
    sync.sync()
    val local = events(ctx).get(localId)!!
    assertEquals("Версия 2", local.title)
    assertEquals(false, local.dirty)
  }

  @Test
  fun googleTimesAreConvertedAndAllDayEndIsInclusive() {
    val timed = Json.parseToJsonElement(
      """{"id":"t","summary":"T","start":{"dateTime":"2026-10-28T09:30:00+03:00"},"end":{"dateTime":"2026-10-28T10:15:00+03:00"}}"""
    )
    val e = eventFromGoogle(timed, "c", utc)!!
    assertEquals("2026-10-28", e.date)
    assertEquals("06:30", e.start)
    assertEquals("07:15", e.end)
    val allDay = Json.parseToJsonElement(
      """{"id":"a","summary":"A","start":{"date":"2026-10-27"},"end":{"date":"2026-10-30"}}"""
    )
    val a = eventFromGoogle(allDay, "c", utc)!!
    assertEquals("2026-10-29", a.endDate)
    assertTrue(a.occursOn(LocalDate(2026, 10, 29)))
    val body = googleBody(a, utc, null)
    assertEquals("2026-10-30", body["end"].let { it as JsonObject }.str("date"))
    val cancelled = Json.parseToJsonElement("""{"id":"x","status":"cancelled","start":{"date":"2026-10-27"}}""")
    assertNull(eventFromGoogle(cancelled, "c", utc))
  }

  @Test
  fun ownEventIdsRoundTrip() {
    val id = "0f8fad5b-d9cb-469f-a165-70867728950e"
    val g = googleEventIdFor(id)!!
    assertTrue(g.all { it in '0'..'9' || it in 'a'..'v' })
    assertEquals(id, docIdForGoogle("any", g))
    assertTrue(docIdForGoogle("cal", "abc_20261028T090000Z").startsWith("gcal-"))
  }

  @Test
  fun pkceMatchesRfcExample() {
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      sha256("abc".encodeToByteArray()).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    )
    assertEquals(
      "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
      Pkce.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
    )
  }
}
