package dev.akkraizen.berill.plugins.health.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.akkraizen.berill.api.PluginContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class DoseStyle { CARDS, TIMELINE, CHIPS }

@Serializable
data class HealthPrefs(
  val doseStyle: DoseStyle = DoseStyle.CARDS,
  val reminders: Boolean = true,
  val reminderLeadMinutes: Int = 0,
  val sleepGoalMinutes: Int = 480,
  val stepsGoal: Int = 8000,
  val homeSummary: Boolean = true
)

val REMINDER_LEADS = listOf(0, 5, 15, 30)
val SLEEP_GOALS = listOf(360, 420, 450, 480, 510, 540)
val STEP_GOALS = listOf(5000, 6000, 8000, 10000, 12000, 15000)

private const val PREFS_KEY = "prefs"
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun healthPrefs(ctx: PluginContext): HealthPrefs =
  ctx.settings.get(PREFS_KEY)?.let { runCatching { json.decodeFromJsonElement(HealthPrefs.serializer(), it) }.getOrNull() } ?: HealthPrefs()

internal fun setHealthPrefs(ctx: PluginContext, prefs: HealthPrefs) =
  ctx.settings.set(PREFS_KEY, json.encodeToJsonElement(HealthPrefs.serializer(), prefs))

@Composable
internal fun rememberHealthPrefs(ctx: PluginContext): HealthPrefs {
  val raw by remember(ctx) { ctx.settings.observe(PREFS_KEY) }.collectAsState(ctx.settings.get(PREFS_KEY))
  return remember(raw) { raw?.let { runCatching { json.decodeFromJsonElement(HealthPrefs.serializer(), it) }.getOrNull() } ?: HealthPrefs() }
}
