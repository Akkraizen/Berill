package dev.akkraizen.berill.plugins.recipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.DeleteButton
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.BodyEditor
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.fields.IntField
import dev.akkraizen.berill.api.ui.fields.SearchField
import dev.akkraizen.berill.api.ui.fields.normalizeCommaList
import dev.akkraizen.berill.api.ui.formatMinutes
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.FieldWithActions
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.plugins.recipes.RecipesPlugin
import dev.akkraizen.berill.plugins.recipes.data.ALL
import dev.akkraizen.berill.plugins.recipes.data.Recipe
import dev.akkraizen.berill.plugins.recipes.data.deleteRecipeImages
import dev.akkraizen.berill.plugins.recipes.data.recipes
import dev.akkraizen.berill.plugins.recipes.data.rememberRecipeImages
import dev.akkraizen.berill.plugins.recipes.i18n.RecipesText

@Composable
internal fun RecipesGallery(ctx: PluginContext) {
  val collection = remember(ctx) { recipes(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  var query by rememberDraft("recipes/gallery/query")
  var favoritesOnly by rememberSaveable { mutableStateOf(false) }
  var category by rememberSaveable { mutableStateOf(ALL) }
  var adding by remember { mutableStateOf(false) }
  val images = rememberRecipeImages(ctx)

  val categories = listOf(ALL) + all.mapNotNull { it.value.category }.distinct().sorted()
  val q = query.trim().lowercase()
  val shown = all.filter {
    q.isEmpty() ||
      it.value.title.lowercase().contains(q) ||
      it.value.tags.any { t -> t.lowercase().contains(q) }
  }
    .filter { !favoritesOnly || it.value.favorite }
    .filter { category == ALL || it.value.category == category }
    .sortedWith(
      compareByDescending<Item<Recipe>> { it.value.favorite }
        .thenBy { it.value.title.lowercase() }
    )

  PluginList {
    item {
      FieldWithActions(
        field = {
          SearchField(query, { query = it }, RecipesText.searchPlaceholder())
        },
        actions = {
          BerillChip(
            selected = favoritesOnly,
            onClick = { favoritesOnly = !favoritesOnly },
            label = { Text(RecipesText.favorites(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
              Ic(if (favoritesOnly) BerillIcons.Star else BerillIcons.StarOutline, size = 18.dp)
            }
          )
          FilledTonalButton(onClick = { adding = true }) {
            Ic(BerillIcons.Add, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(RecipesText.recipe())
          }
        }
      )
    }
    item {
      ChoiceChips(
        categories,
        category,
        { if (it == ALL) RecipesText.all() else RecipesText.categoryLabel(it) },
        { category = it },
        icon = { if (it == ALL) Icons.Outlined.Restaurant else categoryIcon(it) }
      )
    }
    if (shown.isEmpty()) item { EmptyHint(RecipesText.nothingFound()) }
    item {
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 220.dp).toInt().coerceIn(2, 5)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          shown.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              row.forEach { item ->
                RecipeCard(
                  item.value,
                  photo = images[item.id]?.firstOrNull()?.value?.data,
                  onOpen = { ctx.nav.open(RecipesPlugin.RECIPE_ROUTE + item.id) },
                  onFavorite = {
                    collection.get(item.id)?.let {
                      collection.save(item.id, it.copy(favorite = !it.favorite))
                    }
                  },
                  modifier = Modifier.weight(1f)
                )
              }
              repeat(cols - row.size) { Box(Modifier.weight(1f)) }
            }
          }
        }
      }
    }
  }

  if (adding)
    AddRecipeDialog(
      onDismiss = { adding = false },
      onAdd = { recipe ->
        val id = ctx.data.newId()
        collection.save(id, recipe)
        adding = false
        ctx.nav.open(RecipesPlugin.RECIPE_ROUTE + id)
      }
    )
}

@Composable
private fun RecipeCard(
  r: Recipe,
  photo: String?,
  onOpen: () -> Unit,
  onFavorite: () -> Unit,
  modifier: Modifier
) {
  val photoBytes = rememberImageBytes(photo)
  Surface(
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = modifier.interactive(MaterialTheme.shapes.extraLarge, onClick = onOpen)
  ) {
    Column {
      Box {
        Cover(
          r.cover,
          categoryIcon(r.category),
          Modifier.fillMaxWidth().aspectRatio(4f / 3f),
          MaterialTheme.shapes.extraLarge,
          bytes = photoBytes
        )
        FavoriteButton(r.favorite, onFavorite, Modifier.align(Alignment.TopEnd))
      }
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          val muted = MaterialTheme.colorScheme.onSurfaceVariant
          Ic(categoryIcon(r.category), size = 14.dp, tint = muted)
          Text(
            r.category?.let(RecipesText::categoryLabel) ?: RecipesText.noCategory(),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            maxLines = 1
          )
          r.time?.let {
            Spacer(Modifier.width(6.dp))
            Ic(Icons.Outlined.Timer, size = 14.dp, tint = muted)
            Text(formatMinutes(it), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
          }
        }
      }
    }
  }
}

@Composable
private fun AddRecipeDialog(onDismiss: () -> Unit, onAdd: (Recipe) -> Unit) {
  var title by remember { mutableStateOf("") }
  var category by remember { mutableStateOf<String?>(null) }
  val confirm: () -> Unit = {
    onAdd(Recipe(title = title.trim(), category = category, body = RecipesText.bodyTemplate()))
  }
  DialogKeys(onConfirm = confirm, confirmEnabled = title.isNotBlank(), onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(RecipesText.newRecipe()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(title, { title = it }, label = { Text(RecipesText.name(), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true)
        DropdownField(
          RecipesText.category(),
          CATEGORY_ICONS.keys.toList(),
          category,
          { RecipesText.categoryLabel(it) },
          { category = it },
          icon = ::categoryIcon
        )
      }
    },
    confirmButton = {
      TextButton(enabled = title.isNotBlank(), onClick = confirm) { Text(RecipesText.create()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(RecipesText.cancel()) } }
  )
}

@Composable
internal fun RecipeScreen(ctx: PluginContext, id: String) {
  val collection = remember(ctx) { recipes(ctx) }
  val recipe by
    remember(collection, id) { collection.observe(id) }.collectAsState(collection.get(id))
  val r = recipe
    ?: run {
      EmptyHint(RecipesText.recipeDeleted())
      return
    }
  val images = rememberRecipeImages(ctx)
  fun update(transform: (Recipe) -> Recipe) {
    val current = collection.get(id) ?: return
    collection.save(id, transform(current))
  }

  PluginList {
    item {
      DetailHeader(r.title.ifBlank { RecipesText.untitled() }, categoryIcon(r.category)) {
        FavoriteButton(r.favorite, { update { it.copy(favorite = !it.favorite) } })
        DeleteButton(RecipesText.deleteWhat()) {
          deleteRecipeImages(ctx, id)
          collection.delete(id)
          ctx.nav.back()
        }
      }
    }
    item {
      TwoPane(
        left = {
          SectionCard(RecipesText.recipe()) {
            BodyEditor(
              r.body ?: "",
              { v -> update { it.copy(body = v.ifBlank { null }) } },
              RecipesText.bodyPlaceholder()
            )
          }
        },
        right = {
          SectionCard {
            RecipePhotos(ctx, id, r, images[id].orEmpty())
            CommitTextField(r.title, { v -> update { it.copy(title = v.trim()) } }, RecipesText.name())
            DropdownField(
              RecipesText.category(),
              CATEGORY_ICONS.keys.toList(),
              r.category,
              { RecipesText.categoryLabel(it) },
              { c -> update { it.copy(category = c) } },
              Modifier.fillMaxWidth(),
              icon = ::categoryIcon
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              IntField(r.time, { v -> update { it.copy(time = v) } }, RecipesText.timeMin(), Modifier.weight(1f))
              CommitTextField(
                r.servings ?: "",
                { v -> update { it.copy(servings = v.trim().ifEmpty { null }) } },
                RecipesText.servings(),
                Modifier.weight(1f)
              )
            }
            CommitTextField(
              r.tags.joinToString(", "),
              { v ->
                update {
                  it.copy(tags = v.split(',').map(String::trim).filter(String::isNotEmpty))
                }
              },
              RecipesText.tagsCommaSeparated(),
              normalize = ::normalizeCommaList
            )
            CommitTextField(
              r.cover ?: "",
              { v -> update { it.copy(cover = v.trim().ifEmpty { null }) } },
              RecipesText.photoLink()
            )
          }
        }
      )
    }
    item { Box(Modifier.size(1.dp)) }
  }
}

@Composable
private fun FavoriteButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  IconButton(onClick = onClick, modifier = modifier) {
    Icon(
      if (favorite) BerillIcons.Star else BerillIcons.StarOutline,
      contentDescription = if (favorite) RecipesText.removeFromFavorites() else RecipesText.addToFavorites(),
      tint = MaterialTheme.colorScheme.tertiary
    )
  }
}
