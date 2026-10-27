package dev.akkraizen.berill.plugins.recipes.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.plugins.recipes.data.RecipeImage
import dev.akkraizen.berill.plugins.recipes.i18n.RecipesText
import dev.akkraizen.berill.plugins.recipes.i18n.pluginI18n

@Composable
internal fun RecipePhotoViewer(
  images: List<Item<RecipeImage>>,
  selectedId: String,
  onSelect: (String) -> Unit,
  onClose: () -> Unit,
  onMakeCover: (String) -> Unit,
  onMove: (String, Int) -> Unit,
  onDelete: (String) -> Unit
) {
  if (images.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
  val index = images.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
  val image = images[index]
  val focus = remember { FocusRequester() }
  fun navigate(delta: Int) { onSelect(images[(index + delta).coerceIn(0, images.lastIndex)].id) }
  DialogKeys(onConfirm = null, onDismiss = onClose)
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
      Column(Modifier.fillMaxSize().safeDrawingPadding().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
          Key.DirectionLeft -> { navigate(-1); true }
          Key.DirectionRight -> { navigate(1); true }
          Key.Escape -> { onClose(); true }
          else -> false
        }
      }.focusRequester(focus).focusable()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, RecipesText.close()) }
          Text("${index + 1} / ${images.size}", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.weight(1f))
          if (index == 0) Text(pluginI18n.text("photos.cover"), color = MaterialTheme.colorScheme.primary)
          else TextButton(onClick = { onMakeCover(image.id) }) { Text(RecipesText.makeCover()) }
          IconButton(onClick = { onDelete(image.id) }) {
            Icon(Icons.Outlined.Delete, RecipesText.deletePhoto(), tint = MaterialTheme.colorScheme.error)
          }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
          key(image.id) { ZoomableRecipePhoto(image.value.data) }
          FilledTonalIconButton(onClick = { navigate(-1) }, enabled = index > 0, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, pluginI18n.text("photos.previous"))
          }
          FilledTonalIconButton(
            onClick = { navigate(1) },
            enabled = index < images.lastIndex,
            modifier = Modifier.align(Alignment.CenterEnd)
          ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, pluginI18n.text("photos.next"))
          }
        }
        FlowRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
          TextButton(onClick = { onMove(image.id, index - 1) }, enabled = index > 0) {
            Text(pluginI18n.text("photos.moveLeft"))
          }
          TextButton(onClick = { onMove(image.id, index + 1) }, enabled = index < images.lastIndex) {
            Text(pluginI18n.text("photos.moveRight"))
          }
        }
      }
    }
  }
}

@Composable
private fun ZoomableRecipePhoto(data: String) {
  val bytes = rememberImageBytes(data)
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  var size by remember { mutableStateOf(IntSize.Zero) }
  fun zoom(factor: Float, pan: Offset = Offset.Zero) {
    scale = (scale * factor).coerceIn(1f, 5f)
    val x = size.width * (scale - 1f) / 2f
    val y = size.height * (scale - 1f) / 2f
    offset = Offset((offset.x + pan.x).coerceIn(-x, x), (offset.y + pan.y).coerceIn(-y, y))
  }
  val transform = rememberTransformableState { _, zoomChange, panChange, _ -> zoom(zoomChange, panChange) }
  Box(Modifier.fillMaxSize().onSizeChanged { size = it }.clipToBounds()) {
    Box(
      Modifier.fillMaxSize().transformable(transform)
        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom(if (scale > 1f) 1f / scale else 2f) }) }
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              if (event.type == PointerEventType.Scroll) {
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) {
                  zoom(if (delta < 0) 1.15f else 1f / 1.15f)
                  event.changes.forEach { it.consume() }
                }
              }
            }
          }
        }
    ) {
      Cover(null, categoryIcon(""), Modifier.fillMaxSize().graphicsLayer {
        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
      }, shape = RectangleShape, bytes = bytes, contentScale = ContentScale.Fit)
    }
    Row(Modifier.align(Alignment.BottomCenter).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      FilledTonalIconButton(onClick = { zoom(1f / 1.4f) }, enabled = scale > 1f) {
        Icon(Icons.Outlined.ZoomOut, pluginI18n.text("photos.zoomOut"))
      }
      TextButton(onClick = { zoom(1f / scale) }) {
        Text("${(scale * 100).toInt()}%", modifier = Modifier.padding(horizontal = 8.dp))
      }
      FilledTonalIconButton(onClick = { zoom(1.4f) }, enabled = scale < 5f) {
        Icon(Icons.Outlined.ZoomIn, pluginI18n.text("photos.zoomIn"))
      }
    }
  }
}
