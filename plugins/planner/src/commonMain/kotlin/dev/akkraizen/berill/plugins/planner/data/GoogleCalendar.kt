package dev.akkraizen.berill.plugins.planner.data

import dev.akkraizen.berill.api.HttpRequest
import dev.akkraizen.berill.api.HttpResponse
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.arr
import dev.akkraizen.berill.api.formBody
import dev.akkraizen.berill.api.int
import dev.akkraizen.berill.api.obj
import dev.akkraizen.berill.api.string
import dev.akkraizen.berill.api.str
import dev.akkraizen.berill.api.strings
import dev.akkraizen.berill.api.stringList
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.urlEncode
import dev.akkraizen.berill.plugins.planner.PlannerPlugin
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val GOOGLE_SELECTED = "google.calendars"
const val GOOGLE_DEFAULT = "google.defaultCalendar"
const val GOOGLE_CALENDARS = "google.calendarList"

private const val REFRESH_TOKEN = "google.refresh"
private const val ACCESS_TOKEN = "google.access"
private const val ACCESS_EXPIRES = "google.expires"
private const val ACCOUNT = "google.account"

private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
private const val API = "https://www.googleapis.com/calendar/v3"
private const val SCOPES =
  "https://www.googleapis.com/auth/calendar.events https://www.googleapis.com/auth/calendar.calendarlist.readonly"
private const val OWN_PREFIX = "brl"

@Serializable
data class GoogleCalendarInfo(
  val id: String,
  val title: String,
  val color: String? = null,
  val primary: Boolean = false,
  val writable: Boolean = true
)

data class GoogleSyncState(val running: Boolean = false, val lastSync: Long? = null, val error: String? = null)

data class GoogleSignInState(val running: Boolean = false, val error: String? = null)

class GoogleApiException(val status: Int, message: String) : Exception(message)

private val json = Json { ignoreUnknownKeys = true }
private val calendarsSerializer = ListSerializer(GoogleCalendarInfo.serializer())

fun googleEventIdFor(docId: String): String? {
  val hex = docId.replace("-", "").lowercase()
  return if (hex.length == 32 && hex.all { it in '0'..'9' || it in 'a'..'f' }) OWN_PREFIX + hex else null
}

fun docIdForGoogle(calendarId: String, eventId: String): String {
  if (eventId.length == OWN_PREFIX.length + 32 && eventId.startsWith(OWN_PREFIX)) {
    val hex = eventId.removePrefix(OWN_PREFIX)
    if (hex.all { it in '0'..'9' || it in 'a'..'f' })
      return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
  }
  var h = 0x811c9dc5.toInt()
  calendarId.forEach { h = (h xor it.code) * 0x01000193 }
  val tag = h.toUInt().toString(16).padStart(8, '0')
  return "gcal-$tag-${eventId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }}"
}

private val WEEKDAY_CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

private fun compact(date: LocalDate): String = date.toString().replace("-", "")

private fun expand(text: String): LocalDate? =
  text.take(8).takeIf { it.length == 8 && it.all(Char::isDigit) }
    ?.let { "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)}".toDateOrNull() }

fun recurrenceLines(event: CalendarEvent, zone: TimeZone): List<String> {
  val rule = event.repeat ?: return emptyList()
  val first = event.firstDay ?: return emptyList()
  val freq = when (rule.freq) {
    DAILY -> "DAILY"
    MONTHLY -> "MONTHLY"
    YEARLY -> "YEARLY"
    else -> "WEEKLY"
  }
  val parts = mutableListOf("FREQ=$freq")
  if (rule.interval > 1) parts += "INTERVAL=${rule.interval}"
  if (rule.freq == WEEKLY) parts += "BYDAY=" + rule.weekdays(first).joinToString(",") { WEEKDAY_CODES[it - 1] }
  rule.untilDate?.let { until ->
    parts += if (event.allDay)
      "UNTIL=${compact(until)}"
    else
      "UNTIL=" + LocalDateTime(until, timeOfMinutes(24 * 60 - 1)).toInstant(zone).toString()
        .replace("-", "").replace(":", "").substringBefore('.').removeSuffix("Z") + "Z"
  }
  val lines = mutableListOf("RRULE:" + parts.joinToString(";"))
  val skipped = event.exceptions.mapNotNull { it.toDateOrNull() }.sorted()
  if (skipped.isNotEmpty()) {
    lines += if (event.allDay)
      "EXDATE;VALUE=DATE:" + skipped.joinToString(",") { compact(it) }
    else {
      val time = (event.start ?: "00:00").replace(":", "") + "00"
      "EXDATE;TZID=${zone.id}:" + skipped.joinToString(",") { compact(it) + "T" + time }
    }
  }
  return lines
}

fun parseRecurrence(lines: List<String>): Pair<Repeat?, List<String>> {
  var repeat: Repeat? = null
  val exceptions = mutableListOf<String>()
  for (line in lines) {
    when {
      line.startsWith("RRULE:") -> {
        val fields = line.removePrefix("RRULE:").split(';').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val freq = when (fields["FREQ"]) {
          "DAILY" -> DAILY
          "WEEKLY" -> WEEKLY
          "MONTHLY" -> MONTHLY
          "YEARLY" -> YEARLY
          else -> return null to emptyList()
        }
        val days = fields["BYDAY"]?.split(',')?.mapNotNull { code -> WEEKDAY_CODES.indexOf(code.trim()).takeIf { it >= 0 }?.plus(1) }
        if (freq != WEEKLY && fields["BYDAY"] != null) return null to emptyList()
        repeat = Repeat(freq, fields["INTERVAL"]?.toIntOrNull() ?: 1, days.orEmpty(), fields["UNTIL"]?.let(::expand)?.toString())
      }
      line.startsWith("EXDATE") ->
        exceptions += line.substringAfter(':').split(',').mapNotNull { expand(it.trim())?.toString() }
    }
  }
  return repeat to exceptions.distinct().sorted()
}

fun eventFromGoogle(source: JsonElement, calendarId: String, zone: TimeZone): CalendarEvent? {
  val id = source.str("id") ?: return null
  if (source.str("status") == "cancelled") return null
  val start = source.obj("start") ?: return null
  val end = source.obj("end")
  val title = source.str("summary").orEmpty()
  val plain = CalendarEvent(
    title = title,
    location = source.str("location").orEmpty(),
    notes = source.str("description").orEmpty(),
    calendarId = calendarId,
    googleId = id,
    etag = source.str("etag"),
    updatedAt = source.str("updated")?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0
  )
  val props = source.obj("extendedProperties").obj("private")
  val birthday = props.str("berillKind") == BIRTHDAY || source.str("eventType") == BIRTHDAY
  val (repeat, skipped) = parseRecurrence(source.strings("recurrence"))
  val base = plain.copy(
    repeat = repeat ?: if (birthday && source.str("recurringEventId") == null) Repeat(YEARLY) else null,
    exceptions = skipped,
    kind = if (birthday) BIRTHDAY else null,
    birthYear = props.str("berillBirthYear")?.toIntOrNull()
  )
  val startDate = start.str("date")?.toDateOrNull()
  if (startDate != null) {
    val endExclusive = end.str("date")?.toDateOrNull()
    val last = endExclusive?.plusDays(-1)?.takeIf { it > startDate }
    return base.copy(date = startDate.toString(), endDate = last?.toString())
  }
  val s = start.str("dateTime")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
  val e = end.str("dateTime")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: s
  val from = s.toLocalDateTime(zone)
  val to = e.toLocalDateTime(zone)
  return base.copy(
    date = from.date.toString(),
    endDate = to.date.takeIf { it != from.date }?.toString(),
    start = from.time.hhmm(),
    end = to.time.hhmm()
  )
}

fun googleBody(event: CalendarEvent, zone: TimeZone, id: String?): JsonObject =
  buildJsonObject {
    if (id != null) put("id", id)
    put("summary", event.title)
    put("location", event.location)
    put("description", event.notes)
    val first = event.firstDay ?: LocalDate(1970, 1, 1)
    if (event.allDay) {
      val last = event.lastDay ?: first
      put("start", buildJsonObject { put("date", first.toString()) })
      put("end", buildJsonObject { put("date", last.plusDays(1).toString()) })
    } else {
      val s = event.startAt() ?: LocalDateTime(first, timeOfMinutes(0))
      val e = event.endAt()?.takeIf { it > s } ?: plusMinutes(s, 60)
      put(
        "start",
        buildJsonObject {
          put("dateTime", s.toInstant(zone).toString())
          if (event.recurring) put("timeZone", zone.id)
        }
      )
      put(
        "end",
        buildJsonObject {
          put("dateTime", e.toInstant(zone).toString())
          if (event.recurring) put("timeZone", zone.id)
        }
      )
    }
    val instance = event.googleId?.contains('_') == true
    if (!instance) put("recurrence", JsonArray(recurrenceLines(event, zone).map(::JsonPrimitive)))
    if (event.isBirthday)
      put(
        "extendedProperties",
        buildJsonObject {
          put(
            "private",
            buildJsonObject {
              put("berillKind", BIRTHDAY)
              event.birthYear?.let { put("berillBirthYear", it.toString()) }
            }
          )
        }
      )
  }

internal class GoogleCalendarSync private constructor(private val ctx: PluginContext) {
  private val events = ctx.data.collection(PlannerPlugin.EVENTS, CalendarEvent.serializer())
  private val mutex = Mutex()
  private val _state = MutableStateFlow(GoogleSyncState())
  private var scheduled: Job? = null
  private val _signIn = MutableStateFlow(GoogleSignInState())
  private var signInJob: Job? = null

  val state: StateFlow<GoogleSyncState> = _state

  val signInState: StateFlow<GoogleSignInState> = _signIn

  val connected: Flow<Boolean> = ctx.secrets.observe(REFRESH_TOKEN).map { !it.isNullOrEmpty() }

  val account: Flow<String?> = ctx.secrets.observe(ACCOUNT)

  fun isConnected(): Boolean = !ctx.secrets.get(REFRESH_TOKEN).isNullOrEmpty()

  private fun clientId(): String = GoogleClient.ID

  private fun clientSecret(): String = GoogleClient.SECRET

  fun isConfigured(): Boolean = GoogleClient.ID.isNotBlank()

  fun calendars(): List<GoogleCalendarInfo> = decodeCalendars(ctx.settings.get(GOOGLE_CALENDARS))

  fun observeCalendars(): Flow<List<GoogleCalendarInfo>> = ctx.settings.observe(GOOGLE_CALENDARS).map(::decodeCalendars)

  private fun decodeCalendars(value: JsonElement?): List<GoogleCalendarInfo> =
    (value as? JsonArray)?.let { runCatching { json.decodeFromJsonElement(calendarsSerializer, it) }.getOrNull() }.orEmpty()

  fun selected(): List<String> {
    val chosen = ctx.settings.stringList(GOOGLE_SELECTED)
    if (chosen != null) return chosen
    return calendars().filter { it.primary }.map { it.id }
  }

  fun setSelected(ids: List<String>) = ctx.settings.set(GOOGLE_SELECTED, JsonArray(ids.distinct().map(::JsonPrimitive)))

  fun defaultCalendar(): String? {
    val all = calendars()
    val chosen = ctx.settings.string(GOOGLE_DEFAULT)
    return all.firstOrNull { it.id == chosen && it.writable }?.id
      ?: all.firstOrNull { it.primary }?.id
      ?: all.firstOrNull { it.writable }?.id
  }

  fun setDefault(id: String) = ctx.settings.set(GOOGLE_DEFAULT, JsonPrimitive(id))

  fun startSignIn(open: (String) -> Unit) {
    if (signInJob?.isActive == true) return
    _signIn.value = GoogleSignInState(running = true)
    signInJob = ctx.scope.launch {
      try {
        signIn(open)
        _signIn.value = GoogleSignInState()
      } catch (e: CancellationException) {
        _signIn.value = GoogleSignInState()
        throw e
      } catch (e: Exception) {
        _signIn.value = GoogleSignInState(error = e.message ?: e.toString())
      }
    }
  }

  fun cancelSignIn() {
    signInJob?.cancel()
    _signIn.value = GoogleSignInState()
  }

  suspend fun signIn(open: (String) -> Unit) {
    check(isConfigured()) { "Google sign-in is not available in this build" }
    val pkce = Pkce.create()
    val state = Uuid.random().toHexString()
    var redirect = ""
    val params = ctx.oauth.receive { uri ->
      redirect = uri
      open(
        AUTH_URL + "?" + formBody(
          mapOf(
            "client_id" to clientId(),
            "redirect_uri" to uri,
            "response_type" to "code",
            "scope" to SCOPES,
            "code_challenge" to pkce.challenge,
            "code_challenge_method" to "S256",
            "access_type" to "offline",
            "prompt" to "consent",
            "state" to state
          )
        )
      )
    }
    params["error"]?.let { throw GoogleApiException(0, it) }
    if (params["state"] != state) throw GoogleApiException(0, "state mismatch")
    val code = params["code"] ?: throw GoogleApiException(0, "no code")
    val tokens = token(
      mapOf(
        "code" to code,
        "client_id" to clientId(),
        "client_secret" to clientSecret(),
        "redirect_uri" to redirect,
        "grant_type" to "authorization_code",
        "code_verifier" to pkce.verifier
      )
    )
    val refresh = tokens.str("refresh_token") ?: throw GoogleApiException(0, "no refresh token")
    ctx.secrets.set(REFRESH_TOKEN, refresh)
    storeAccess(tokens)
    val list = loadCalendars()
    ctx.secrets.set(ACCOUNT, list.firstOrNull { it.primary }?.id)
    if (ctx.settings.stringList(GOOGLE_SELECTED) == null) setSelected(list.filter { it.primary }.map { it.id })
    sync()
  }

  suspend fun signOut() {
    val refresh = ctx.secrets.get(REFRESH_TOKEN)
    if (refresh != null)
      runCatching {
        ctx.http.send(HttpRequest("POST", REVOKE_URL, body = formBody(mapOf("token" to refresh)), contentType = FORM))
      }
    listOf(REFRESH_TOKEN, ACCESS_TOKEN, ACCESS_EXPIRES, ACCOUNT).forEach { ctx.secrets.set(it, null) }
    _state.value = GoogleSyncState()
  }

  private suspend fun token(fields: Map<String, String>): JsonElement {
    val r = ctx.http.send(HttpRequest("POST", TOKEN_URL, body = formBody(fields), contentType = FORM))
    val body = runCatching { Json.parseToJsonElement(r.body) }.getOrNull()
    if (!r.ok) {
      val error = body.str("error") ?: "HTTP ${r.status}"
      if (error == "invalid_grant") listOf(REFRESH_TOKEN, ACCESS_TOKEN, ACCESS_EXPIRES).forEach { ctx.secrets.set(it, null) }
      throw GoogleApiException(r.status, error)
    }
    return body ?: throw GoogleApiException(r.status, "bad token response")
  }

  private fun storeAccess(tokens: JsonElement) {
    ctx.secrets.set(ACCESS_TOKEN, tokens.str("access_token"))
    val seconds = tokens.int("expires_in") ?: 3600
    ctx.secrets.set(ACCESS_EXPIRES, (nowMillis() + seconds * 1000L).toString())
  }

  private suspend fun accessToken(force: Boolean = false): String {
    val current = ctx.secrets.get(ACCESS_TOKEN)
    val expires = ctx.secrets.get(ACCESS_EXPIRES)?.toLongOrNull() ?: 0
    if (!force && current != null && expires - 60_000 > nowMillis()) return current
    val refresh = ctx.secrets.get(REFRESH_TOKEN) ?: throw GoogleApiException(401, "not signed in")
    val tokens = token(
      mapOf(
        "refresh_token" to refresh,
        "client_id" to clientId(),
        "client_secret" to clientSecret(),
        "grant_type" to "refresh_token"
      )
    )
    storeAccess(tokens)
    return tokens.str("access_token") ?: throw GoogleApiException(0, "no access token")
  }

  private suspend fun api(method: String, url: String, body: JsonObject? = null, headers: Map<String, String> = emptyMap()): HttpResponse {
    suspend fun call(token: String) =
      ctx.http.send(
        HttpRequest(
          method,
          url,
          headers + mapOf("Authorization" to "Bearer $token", "Accept" to "application/json"),
          body?.toString(),
          if (body != null) "application/json" else null
        )
      )
    val first = call(accessToken())
    return if (first.status == 401) call(accessToken(force = true)) else first
  }

  private fun fail(r: HttpResponse): Nothing {
    val message = runCatching { Json.parseToJsonElement(r.body).obj("error").str("message") }.getOrNull()
    throw GoogleApiException(r.status, message ?: "HTTP ${r.status}")
  }

  suspend fun loadCalendars(): List<GoogleCalendarInfo> {
    val r = api("GET", "$API/users/me/calendarList?maxResults=250")
    if (!r.ok) fail(r)
    val list = Json.parseToJsonElement(r.body).arr("items").mapNotNull { item ->
      val id = item.str("id") ?: return@mapNotNull null
      val role = item.str("accessRole")
      GoogleCalendarInfo(
        id = id,
        title = item.str("summaryOverride") ?: item.str("summary") ?: id,
        color = item.str("backgroundColor"),
        primary = (item as? JsonObject)?.get("primary")?.toString() == "true",
        writable = role == "owner" || role == "writer"
      )
    }
      .sortedWith(compareBy({ !it.primary }, { it.title.lowercase() }))
    ctx.settings.set(GOOGLE_CALENDARS, json.encodeToJsonElement(calendarsSerializer, list))
    return list
  }

  fun scheduleSync(delayMillis: Long = 1500) {
    if (!isConnected()) return
    scheduled?.cancel()
    scheduled = ctx.scope.launch {
      delay(delayMillis)
      runCatching { sync() }
    }
  }

  suspend fun sync() {
    if (!isConnected()) return
    mutex.withLock {
      _state.value = _state.value.copy(running = true, error = null)
      try {
        val zone = TimeZone.currentSystemDefault()
        if (calendars().isEmpty()) loadCalendars()
        push(zone)
        pull(zone)
        _state.value = GoogleSyncState(lastSync = nowMillis())
      } catch (e: Exception) {
        _state.value = _state.value.copy(running = false, error = e.message ?: e.toString())
        throw e
      }
    }
  }

  private fun eventsUrl(calendarId: String) = "$API/calendars/${calendarId.urlEncode()}/events"

  private fun eventUrl(calendarId: String, eventId: String) = eventsUrl(calendarId) + "/" + eventId.urlEncode()

  private fun saveSynced(id: String, pushed: CalendarEvent, update: (CalendarEvent) -> CalendarEvent) {
    val current = events.get(id) ?: return
    val next = update(current)
    events.save(id, if (current.updatedAt == pushed.updatedAt) next.copy(dirty = false) else next.copy(dirty = true))
  }

  private suspend fun push(zone: TimeZone) {
    val fallback = defaultCalendar()
    for (item in events.all().filter { it.value.dirty }) {
      val e = item.value
      val cal = e.calendarId
      val gid = e.googleId
      if (e.deleted) {
        if (cal != null && gid != null) {
          val r = api("DELETE", eventUrl(cal, gid))
          if (!r.ok && r.status != 404 && r.status != 410) fail(r)
        }
        events.delete(item.id)
        continue
      }
      if (gid == null || cal == null) {
        val target = cal ?: fallback ?: continue
        val newId = googleEventIdFor(item.id)
        var r = api("POST", eventsUrl(target), googleBody(e, zone, newId))
        if (r.status == 409 && newId != null) {
          r = api("PUT", eventUrl(target, newId), googleBody(e, zone, null).plusStatus())
        }
        if (!r.ok) fail(r)
        val created = Json.parseToJsonElement(r.body)
        saveSynced(item.id, e) { it.copy(calendarId = target, googleId = created.str("id"), etag = created.str("etag")) }
        continue
      }
      val r = api("PATCH", eventUrl(cal, gid), googleBody(e, zone, null), e.etag?.let { mapOf("If-Match" to it) }.orEmpty())
      when {
        r.ok -> saveSynced(item.id, e) { it.copy(etag = Json.parseToJsonElement(r.body).str("etag")) }
        r.status == 412 -> {
          val remote = api("GET", eventUrl(cal, gid))
          if (!remote.ok) fail(remote)
          eventFromGoogle(Json.parseToJsonElement(remote.body), cal, zone)?.let { events.save(item.id, it) } ?: events.delete(item.id)
        }
        r.status == 404 || r.status == 410 -> events.delete(item.id)
        else -> fail(r)
      }
    }
  }

  private fun JsonObject.plusStatus(): JsonObject = JsonObject(this + ("status" to JsonPrimitive("confirmed")))

  private suspend fun list(calendarId: String, from: LocalDate, to: LocalDate, zone: TimeZone): List<JsonElement> {
    val out = ArrayList<JsonElement>()
    var page: String? = null
    do {
      val query = mutableMapOf(
        "singleEvents" to "true",
        "maxResults" to "2500",
        "timeMin" to LocalDateTime(from, timeOfMinutes(0)).toInstant(zone).toString(),
        "timeMax" to LocalDateTime(to, timeOfMinutes(0)).toInstant(zone).toString()
      )
      page?.let { query["pageToken"] = it }
      val r = api("GET", eventsUrl(calendarId) + "?" + formBody(query))
      if (!r.ok) fail(r)
      val body = Json.parseToJsonElement(r.body)
      out += body.arr("items")
      page = body.str("nextPageToken")
    } while (page != null)
    return out
  }

  private suspend fun pull(zone: TimeZone) {
    val selected = selected().toSet()
    val from = today().plusDays(-PAST_DAYS)
    val to = today().plusDays(FUTURE_DAYS)
    val local = events.all()
    val byGoogle = local.filter { it.value.googleId != null }.associateBy { it.value.calendarId to it.value.googleId }
    for (cal in selected) {
      val seen = HashSet<String>()
      for (raw in list(cal, from, to, zone)) {
        val parent = raw.str("recurringEventId")
        if (parent != null) {
          val master = byGoogle[cal to parent]
          if (master != null || parent.startsWith(OWN_PREFIX)) {
            seen += master?.id ?: docIdForGoogle(cal, parent)
            continue
          }
        }
        val remote = eventFromGoogle(raw, cal, zone) ?: continue
        val gid = remote.googleId ?: continue
        val existing = byGoogle[cal to gid]
        val id = existing?.id ?: docIdForGoogle(cal, gid)
        seen += id
        val current = existing?.value ?: events.get(id)
        if (current != null && current.dirty) continue
        if (current != null && current.etag == remote.etag && !current.deleted) continue
        events.save(id, remote)
      }
      local
        .filter { it.value.calendarId == cal && it.value.googleId != null && !it.value.dirty && it.id !in seen }
        .filter { !it.value.recurring }
        .filter { item -> item.value.firstDay?.let { it >= from && it < to } == true }
        .forEach { events.delete(it.id) }
      refreshSeries(cal, local, zone)
    }
    local
      .filter { it.value.googleId != null && it.value.calendarId !in selected && !it.value.dirty }
      .forEach { events.delete(it.id) }
  }

  private suspend fun refreshSeries(calendarId: String, local: List<Item<CalendarEvent>>, zone: TimeZone) {
    val series = local.filter {
      it.value.calendarId == calendarId && it.value.recurring && it.value.googleId != null && !it.value.dirty && !it.value.deleted
    }
    for (item in series) {
      val gid = item.value.googleId ?: continue
      val r = api("GET", eventUrl(calendarId, gid))
      if (r.status == 404 || r.status == 410) {
        events.delete(item.id)
        continue
      }
      if (!r.ok) fail(r)
      val remote = eventFromGoogle(Json.parseToJsonElement(r.body), calendarId, zone)
      val current = events.get(item.id) ?: continue
      when {
        current.dirty -> Unit
        remote == null -> events.delete(item.id)
        remote.etag != current.etag -> events.save(item.id, remote)
      }
    }
  }

  companion object {
    private const val FORM = "application/x-www-form-urlencoded"
    const val PAST_DAYS = 90
    const val FUTURE_DAYS = 400

    private val instances = HashMap<String, GoogleCalendarSync>()

    fun of(ctx: PluginContext): GoogleCalendarSync = instances.getOrPut(ctx.pluginId) { GoogleCalendarSync(ctx) }
  }
}
