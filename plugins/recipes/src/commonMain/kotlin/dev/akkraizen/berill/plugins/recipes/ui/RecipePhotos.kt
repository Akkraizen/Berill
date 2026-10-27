package dev.akkraizen.berill.plugins.recipes.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PickedFile
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.recipes.data.Recipe
import dev.akkraizen.berill.plugins.recipes.data.RecipeImage
import dev.akkraizen.berill.plugins.recipes.data.moveRecipeImage
import dev.akkraizen.berill.plugins.recipes.data.recipeImages
import dev.akkraizen.berill.plugins.recipes.i18n.RecipesText
import dev.akkraizen.berill.plugins.recipes.i18n.pluginI18n
import kotlin.io.encoding.Base64
import kotlinx.coroutines.launch

@Composable
internal fun rememberImageBytes(data: String?): ByteArray? = remember(data) { data?.let { runCatching { Base64.decode(it) }.getOrNull() } }

@Composable
internal fun RecipePhotos(ctx: PluginContext, recipeId: String, recipe: Recipe, images: List<Item<RecipeImage>>) {
  val collection = remember(ctx) { recipeImages(ctx) }
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var viewing by remember { mutableStateOf<String?>(null) }
  val currentImages by rememberUpdatedState(images)
  var dragging by remember { mutableStateOf<String?>(null) }
  var dragOffset by remember { mutableStateOf(0f) }
  val stride = with(LocalDensity.current) { 80.dp.toPx() }

  fun reorder(id: String, target: Int) {
    moveRecipeImage(currentImages, id, target).forEachIndexed { index, image ->
      if (image.value.position != index.toLong()) collection.save(image.id, image.value.copy(position = index.toLong()))
    }
  }

  fun addFiles(files: List<PickedFile>) {
    val last = collection.all().filter { it.value.recipe == recipeId }
      .maxOfOrNull { it.value.position ?: it.value.createdAt } ?: -1L
    val created = nowMillis()
    files.forEachIndexed { index, file ->
      collection.save(ctx.data.newId(), RecipeImage(
        recipeId,
        Base64.encode(file.bytes),
        file.mimeType,
        created + index,
        last + index + 1
      ))
    }
  }

  fun attach() {
    if (busy) return
    scope.launch {
      busy = true
      try {
        addFiles(ctx.files.pickImages())
      } finally {
        busy = false
      }
    }
  }

  Column(
    Modifier.fillMaxWidth().then(ctx.files.imageDropTarget(enabled = !busy, onDrop = ::addFiles)),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    val main = images.firstOrNull()
    val mainBytes = rememberImageBytes(main?.value?.data)
    Cover(
      recipe.cover,
      categoryIcon(recipe.category),
      Modifier.fillMaxWidth()
        .aspectRatio(4f / 3f)
        .then(
          if (main != null)
            Modifier.interactive(MaterialTheme.shapes.medium) { viewing = main.id }
          else
            Modifier
        ),
      bytes = mainBytes
    )
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      images.forEach { image ->
        val bytes = rememberImageBytes(image.value.data)
        Cover(
          null,
          categoryIcon(recipe.category),
          Modifier.size(72.dp)
            .zIndex(if (dragging == image.id) 1f else 0f)
            .graphicsLayer { if (dragging == image.id) { translationX = dragOffset; scaleX = 1.08f; scaleY = 1.08f } }
            .pointerInput(image.id, stride) {
              detectDragGesturesAfterLongPress(
                onDragStart = { dragging = image.id; dragOffset = 0f },
                onDragCancel = { dragging = null; dragOffset = 0f },
                onDragEnd = {
                  val from = currentImages.indexOfFirst { it.id == image.id }
                  reorder(image.id, from + kotlin.math.round(dragOffset / stride).toInt())
                  dragging = null
                  dragOffset = 0f
                },
                onDrag = { change, amount -> change.consume(); dragOffset += amount.x }
              )
            }.interactive(MaterialTheme.shapes.small) { viewing = image.id },
          MaterialTheme.shapes.small,
          bytes = bytes
        )
      }

      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.size(72.dp).interactive(MaterialTheme.shapes.small, enabled = !busy) {
          attach()
        }
      ) {
        Box(contentAlignment = Alignment.Center) {
          if (busy)
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
          else
            Ic(Icons.Outlined.AddPhotoAlternate, size = 28.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
      }
    }
    if (images.isEmpty()) {
      Text(
        RecipesText.attachHint(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Text(
      pluginI18n.text(if (ctx.files.supportsImageDrop) "photos.desktopHint" else "photos.orderHint"),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }

  viewing?.let { selected ->
    RecipePhotoViewer(
      images = images,
      selectedId = selected,
      onSelect = { viewing = it },
      onClose = { viewing = null },
      onMakeCover = { reorder(it, 0) },
      onMove = ::reorder,
      onDelete = { id -> collection.delete(id); viewing = null }
    )
  }
}
