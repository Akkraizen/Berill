package dev.akkraizen.berill.plugins.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.observeStringList
import dev.akkraizen.berill.api.setStringList
import dev.akkraizen.berill.api.stringList
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.state.rememberDraft

internal data class Catalog(val visible: List<CatalogExercise>, val hidden: List<CatalogExercise>) {
  fun find(id: String): CatalogExercise? = visible.firstOrNull { it.id == id } ?: hidden.firstOrNull { it.id == id }
}

@Composable
internal fun rememberCatalog(ctx: PluginContext): Catalog {
  val collection = remember(ctx) { customExercises(ctx) }
  val custom by remember(collection) { collection.observe() }.collectAsState(emptyList())
  val hiddenIds by
    remember(ctx) { ctx.settings.observeStringList(HIDDEN_EXERCISES_KEY) }
      .collectAsState(ctx.settings.stringList(HIDDEN_EXERCISES_KEY))
  val hiddenSet = hiddenIds.orEmpty().toSet()
  val builtins = builtinExercises()
  val mine = custom.map { it.value.toCatalog(it.id) }
  return Catalog(
    visible = (builtins.filter { it.id !in hiddenSet } + mine).sortedBy { it.name.lowercase() },
    hidden = builtins.filter { it.id in hiddenSet }.sortedBy { it.name.lowercase() }
  )
}

internal fun setHidden(ctx: PluginContext, id: String, hidden: Boolean) {
  val current = ctx.settings.stringList(HIDDEN_EXERCISES_KEY).orEmpty()
  ctx.settings.setStringList(HIDDEN_EXERCISES_KEY, if (hidden) (current + id).distinct() else current - id)
}

internal fun List<CatalogExercise>.filtered(query: String, muscle: String?, equipment: String?): List<CatalogExercise> {
  val q = query.trim().lowercase()
  return filter { ex ->
    (muscle == null || ex.muscle == muscle) &&
      (equipment == null || ex.equipment == equipment) &&
      (q.isEmpty() || ex.aliases.any { it.lowercase().contains(q) })
  }
}

@Composable
internal fun CatalogPage(ctx: PluginContext) {
  val route by ctx.nav.route.collectAsState()
  AnimatedRoute(route) { r ->
    if (r != null && r.startsWith(WorkoutsPlugin.EXERCISE_ROUTE))
      ExerciseScreen(ctx, r.removePrefix(WorkoutsPlugin.EXERCISE_ROUTE))
    else
      CatalogList(ctx)
  }
}

@Composable
private fun CatalogList(ctx: PluginContext) {
  val catalog = rememberCatalog(ctx)
  val images = rememberExerciseImages(ctx)
  var query by rememberDraft("workouts/catalog/search")
  var muscle by rememberSaveable { mutableStateOf<String?>(null) }
  var equipment by rememberSaveable { mutableStateOf<String?>(null) }
  var showHidden by rememberSaveable { mutableStateOf(false) }
  var creating by remember { mutableStateOf(false) }
  val found = catalog.visible.filtered(query, muscle, equipment)
  val groups = MUSCLES.mapNotNull { m ->
    found.filter { it.muscle == m }.takeIf { it.isNotEmpty() }?.let { m to it }
  }

  PluginList {
    item {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(
          query,
          { query = it },
          placeholder = { Text(CatalogText.searchPlaceholder()) },
          leadingIcon = { Ic(Icons.Outlined.Search) },
          singleLine = true,
          modifier = Modifier.weight(1f),
          onSubmit = {
            found.singleOrNull()?.let { ctx.nav.open(WorkoutsPlugin.EXERCISE_ROUTE + it.id) }
          }
        )
        FilledTonalButton(onClick = { creating = true }) {
          Ic(BerillIcons.Add, size = 18.dp)
          Spacer(Modifier.width(6.dp))
          Text(CatalogText.newExercise())
        }
      }
    }
    item {
      ChoiceChips(
        listOf<String?>(null) + MUSCLES,
        muscle,
        { it?.let(CatalogText::muscleLabel) ?: CatalogText.allMuscles() },
        { muscle = it }
      )
    }
    item {
      ChoiceChips(
        listOf<String?>(null) + EQUIPMENT,
        equipment,
        { it?.let(CatalogText::equipmentLabel) ?: CatalogText.anyEquipment() },
        { equipment = it }
      )
    }
    if (groups.isEmpty()) item { EmptyHint(CatalogText.nothing()) }
    groups.forEach { (m, list) ->
      item(key = "group-$m") {
        SectionCard(
          CatalogText.muscleLabel(m),
          trailing = {
            Text(
              CatalogText.count(list.size),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        ) {
          list.forEach { ex ->
            CatalogRow(ex, images.of(ex)) { ctx.nav.open(WorkoutsPlugin.EXERCISE_ROUTE + ex.id) }
          }
        }
      }
    }
    if (catalog.hidden.isNotEmpty())
      item {
        TextButton(onClick = { showHidden = !showHidden }) {
          Ic(if (showHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, size = 18.dp)
          Spacer(Modifier.width(6.dp))
          Text("${CatalogText.showHidden()} (${catalog.hidden.size})")
        }
      }
    if (showHidden && catalog.hidden.isNotEmpty())
      item {
        SectionCard(CatalogText.hiddenTitle(), Icons.Outlined.VisibilityOff) {
          catalog.hidden.forEach { ex ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(ex.name, modifier = Modifier.weight(1f))
              TextButton(onClick = { setHidden(ctx, ex.id, false) }) {
                Text(CatalogText.restore())
              }
            }
          }
        }
      }
  }

  if (creating) {
    ExerciseDialog(
      initial = null,
      muscle = muscle ?: MUSCLE_CHEST,
      existing = catalog.visible,
      onDismiss = { creating = false },
      onSave = { ex ->
        val id = ctx.data.newId()
        customExercises(ctx).save(id, ex.copy(createdAt = nowMillis()))
        creating = false
      }
    )
  }
}

@Composable
internal fun CatalogRow(ex: CatalogExercise, image: DbExercise?, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth()
      .interactive(MaterialTheme.shapes.small, bleed = 6.dp, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    ExerciseThumb(image)
    Text(ex.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (ex.custom)
      Text(
        CatalogText.custom(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary
      )
    Text(
      CatalogText.equipmentLabel(ex.equipment),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
