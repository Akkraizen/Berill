package dev.akkraizen.berill.plugins.nutrition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.akkraizen.berill.api.PluginContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private const val PROFILE_KEY = "profile"
private const val TARGETS_KEY = "targets"
private const val GLASS_KEY = "glass"

const val DEFAULT_GLASS = 250
val GLASS_SIZES = listOf(150, 200, 250, 300, 500)

private fun JsonElement?.toProfile(): Profile =
  this?.let {
    runCatching { NutritionJson.decodeFromJsonElement(Profile.serializer(), it) }.getOrNull()
  } ?: Profile()

private fun JsonElement?.toTargets(): Targets =
  this?.let {
    runCatching { NutritionJson.decodeFromJsonElement(Targets.serializer(), it) }.getOrNull()
  } ?: Targets()

internal fun profileOf(ctx: PluginContext): Profile = ctx.settings.get(PROFILE_KEY).toProfile()

internal fun targetsOf(ctx: PluginContext): Targets = ctx.settings.get(TARGETS_KEY).toTargets()

internal fun setProfile(
  ctx: PluginContext,
  p: Profile
) = ctx.settings.set(PROFILE_KEY, NutritionJson.encodeToJsonElement(Profile.serializer(), p))

internal fun setTargets(
  ctx: PluginContext,
  t: Targets
) = ctx.settings.set(TARGETS_KEY, NutritionJson.encodeToJsonElement(Targets.serializer(), t))

internal fun setGlass(ctx: PluginContext, ml: Int) = ctx.settings.set(GLASS_KEY, JsonPrimitive(ml))

internal data class NutritionPrefs(
  val profile: Profile,
  val targets: Targets,
  val weightKg: Double,
  val computed: Norm,
  val norm: Norm,
  val glassMl: Int
)

@Composable
internal fun rememberNutritionPrefs(ctx: PluginContext): NutritionPrefs {
  val s = ctx.settings
  val profileJson by remember(ctx) { s.observe(PROFILE_KEY) }.collectAsState(s.get(PROFILE_KEY))
  val targetsJson by remember(ctx) { s.observe(TARGETS_KEY) }.collectAsState(s.get(TARGETS_KEY))
  val glassJson by remember(ctx) { s.observe(GLASS_KEY) }.collectAsState(s.get(GLASS_KEY))
  val weightCollection = remember(ctx) { weights(ctx) }
  val weightItems by
    remember(weightCollection) { weightCollection.observe() }.collectAsState(emptyList())

  val profile = profileJson.toProfile()
  val targets = targetsJson.toTargets()
  val weight = latestWeight(weightItems.map { it.value }, profile)
  val computed = computeNorm(profile, weight)
  return NutritionPrefs(
    profile = profile,
    targets = targets,
    weightKg = weight,
    computed = computed,
    norm = computed.withOverrides(targets),
    glassMl = (glassJson as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: DEFAULT_GLASS
  )
}
