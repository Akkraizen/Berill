package dev.akkraizen.berill.plugins.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.cards.Stat
import dev.akkraizen.berill.api.ui.cards.StatTiles
import dev.akkraizen.berill.api.ui.feedback.Tone
import dev.akkraizen.berill.api.ui.fields.DoubleField
import dev.akkraizen.berill.api.ui.fields.IntField
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.DropdownField

@Composable
internal fun NormPage(ctx: PluginContext) {
  val prefs = rememberNutritionPrefs(ctx)
  PluginList {
    item {
      TwoPane(
        left = { ProfileCard(ctx, prefs) },
        right = {
          CalculationCard(prefs)
          OverridesCard(ctx, prefs)
        },
        leftWeight = 1f
      )
    }
  }
}

@Composable
internal fun NormContent(ctx: PluginContext) {
  val prefs = rememberNutritionPrefs(ctx)
  ProfileCard(ctx, prefs)
  CalculationCard(prefs)
  OverridesCard(ctx, prefs)
}

private fun updateProfile(ctx: PluginContext, change: (Profile) -> Profile) = setProfile(ctx, change(profileOf(ctx)))

private fun updateTargets(ctx: PluginContext, change: (Targets) -> Targets) = setTargets(ctx, change(targetsOf(ctx)))

@Composable
private fun ProfileCard(ctx: PluginContext, prefs: NutritionPrefs) {
  val p = prefs.profile
  val weightCollection = remember(ctx) { weights(ctx) }
  val weightItems by
    remember(weightCollection) { weightCollection.observe() }.collectAsState(emptyList())

  SectionCard(NutritionText.profile(), Icons.Outlined.Person) {
    Label(NutritionText.sex())
    ChoiceChips(
      listOf(SEX_MALE, SEX_FEMALE),
      p.sex,
      { if (it == SEX_FEMALE) NutritionText.female() else NutritionText.male() },
      { s -> updateProfile(ctx) { it.copy(sex = s) } }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      IntField(
        p.age,
        { v ->
          v?.takeIf { it in 10..110 }?.let { a -> updateProfile(ctx) { it.copy(age = a) } }
        },
        NutritionText.age(),
        Modifier.weight(1f)
      )
      DoubleField(
        p.heightCm,
        { v ->
          v?.takeIf { it in 100.0..250.0 }
            ?.let { h -> updateProfile(ctx) { it.copy(heightCm = h) } }
        },
        NutritionText.height(),
        Modifier.weight(1f)
      )
    }
    if (weightItems.isEmpty()) {
      DoubleField(
        p.weightKg,
        { v ->
          v?.takeIf { it in 20.0..400.0 }
            ?.let { w -> updateProfile(ctx) { it.copy(weightKg = w) } }
        },
        NutritionText.weightKg()
      )
      Hint(NutritionText.weightFromLog())
    } else {
      Text(NutritionText.weightFromLogValue(formatNum(prefs.weightKg))(), style = MaterialTheme.typography.bodyMedium)
    }
    DropdownField(
      label = NutritionText.activity(),
      options = ACTIVITY_LEVELS,
      selected = ACTIVITY_LEVELS.minByOrNull { kotlin.math.abs(it - p.activity) },
      text = { NutritionText.activityLabel(it) },
      onSelect = { a -> updateProfile(ctx) { it.copy(activity = a) } }
    )
    Label(NutritionText.goal())
    ChoiceChips(
      listOf(GOAL_LOSE, GOAL_MAINTAIN, GOAL_GAIN),
      p.goal,
      { NutritionText.goalLabel(it) },
      { g -> updateProfile(ctx) { it.copy(goal = g) } }
    )
    if (p.goal != GOAL_MAINTAIN) {
      Label(NutritionText.rate())
      ChoiceChips(
        RATES,
        RATES.minByOrNull { kotlin.math.abs(it - p.rateKg) } ?: 0.5,
        { NutritionText.rateLabel(it) },
        { r -> updateProfile(ctx) { it.copy(rateKg = r) } }
      )
    }
  }
}

@Composable
private fun CalculationCard(prefs: NutritionPrefs) {
  val c = prefs.computed
  SectionCard(NutritionText.calculation(), Icons.Outlined.Calculate) {
    StatTiles(
      listOf(
        Stat(NutritionText.bmr(), NutritionText.kcalValue(c.bmr.toString())(), sub = NutritionText.bmrHint()),
        Stat(NutritionText.tdee(), NutritionText.kcalValue(c.tdee.toString())(), sub = NutritionText.tdeeHint()),
        Stat(
          NutritionText.dailyTarget(),
          NutritionText.kcalValue(prefs.norm.kcal.toString())(),
          sub =
            "${NutritionText.proteinShort()} ${prefs.norm.protein} · ${NutritionText.fatShort()} ${prefs.norm.fat} · ${NutritionText.carbsShort()} ${prefs.norm.carbs}",
          tone = Tone.Good
        )
      ),
      minTileWidth = 130.dp
    )
    Hint(NutritionText.formulaHint())
    Hint(NutritionText.minimumNote())
  }
}

@Composable
private fun OverridesCard(ctx: PluginContext, prefs: NutritionPrefs) {
  val c = prefs.computed
  val t = prefs.targets
  SectionCard(
    NutritionText.overrides(),
    Icons.Outlined.Edit,
    trailing = {
      if (t != Targets())
        TextButton(onClick = { setTargets(ctx, Targets()) }) {
          Text(NutritionText.resetOverrides())
        }
    }
  ) {
    Hint(NutritionText.overridesHint())
    IntField(
      t.kcal,
      { v -> updateTargets(ctx) { it.copy(kcal = v?.takeIf { k -> k > 0 }) } },
      NutritionText.auto(NutritionText.kcalField(), c.kcal.toString())()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      IntField(
        t.protein,
        { v -> updateTargets(ctx) { it.copy(protein = v?.takeIf { g -> g >= 0 }) } },
        NutritionText.auto(NutritionText.proteinField(), c.protein.toString())(),
        Modifier.weight(1f)
      )
      IntField(
        t.fat,
        { v -> updateTargets(ctx) { it.copy(fat = v?.takeIf { g -> g >= 0 }) } },
        NutritionText.auto(NutritionText.fatField(), c.fat.toString())(),
        Modifier.weight(1f)
      )
      IntField(
        t.carbs,
        { v -> updateTargets(ctx) { it.copy(carbs = v?.takeIf { g -> g >= 0 }) } },
        NutritionText.auto(NutritionText.carbsField(), c.carbs.toString())(),
        Modifier.weight(1f)
      )
    }
    IntField(
      t.waterMl,
      { v -> updateTargets(ctx) { it.copy(waterMl = v?.takeIf { m -> m > 0 }) } },
      NutritionText.auto(NutritionText.waterField(), c.waterMl.toString())()
    )
    Label(NutritionText.glassSize())
    ChoiceChips(GLASS_SIZES, prefs.glassMl, { NutritionText.mlValue(it.toString())() }, { setGlass(ctx, it) })
  }
}

@Composable
private fun Label(text: String) =
  Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Hint(text: String) =
  Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
