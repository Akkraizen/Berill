package dev.akkraizen.berill.core.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.interaction.mouseScrollable
import dev.akkraizen.berill.core.ui.media.ImagePickerActions
import dev.akkraizen.berill.core.ui.media.ImagePickerHeader
import dev.akkraizen.berill.core.ui.media.ImagePickerPlaceholder
import java.awt.EventQueue
import java.awt.Image
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object DesktopImagePicker {
  private val mutex = Mutex()
  private var pending by mutableStateOf<CancellableContinuation<List<DesktopImageSource>>?>(null)

  private var multiple by mutableStateOf(false)

  internal suspend fun pick(allowMultiple: Boolean): List<DesktopImageSource> = mutex.withLock {
    withContext(Dispatchers.Main) {
      suspendCancellableCoroutine { request ->
        multiple = allowMultiple
        pending = request
        request.invokeOnCancellation {
          EventQueue.invokeLater { if (pending === request) pending = null }
        }
      }
    }
  }

  private fun finish(source: List<DesktopImageSource>) {
    val request = pending
    pending = null
    if (request?.isActive == true) request.resume(source)
  }

  @Composable
  fun Content() {
    val request = pending ?: return
    DisposableEffect(request) {
      onDispose { if (pending === request) finish(emptyList()) }
    }
    ImagePickerDialog(multiple, onDismiss = { finish(emptyList()) }, onChoose = { finish(it) })
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ImagePasteShortcut(onPaste: () -> Unit) {
  val owner = LocalAwtWindow.current
  val currentPaste by rememberUpdatedState(onPaste)
  DisposableEffect(owner) {
    val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val dispatcher = KeyEventDispatcher { event ->
      val window = (event.component as? Window) ?: event.component?.let(SwingUtilities::getWindowAncestor)
      val belongsToPicker = owner != null && generateSequence(window) { it.owner }.any { it === owner }
      if (belongsToPicker && isImagePasteKey(event)) {
        currentPaste()
        event.consume()
        true
      } else false
    }
    manager.addKeyEventDispatcher(dispatcher)
    onDispose { manager.removeKeyEventDispatcher(dispatcher) }
  }
}

@Composable
private fun ImagePickerDialog(multiple: Boolean, onDismiss: () -> Unit, onChoose: (List<DesktopImageSource>) -> Unit) {
  var candidates by remember { mutableStateOf(emptyList<DesktopImagePreview>()) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val focus = remember { FocusRequester() }
  var rootFocused by remember { mutableStateOf(false) }

  suspend fun show(sources: List<DesktopImageSource>) {
    error = null
    loading = true
    try {
      val incoming = if (multiple) sources else sources.take(1)
      val previews = withContext(Dispatchers.IO) {
        incoming.mapNotNull { source ->
          runCatching {
            val image = when (source) {
              is DesktopImageSource.FromClipboard -> source.image
              is DesktopImageSource.FromFile -> ImageIO.read(source.file)
            }
            image?.let {
              val thumbnail = ImageIO.read(ByteArrayInputStream(downscaleToJpeg(it, 320)))
              DesktopImagePreview(source, thumbnail.toComposeImageBitmap())
            }
          }.getOrNull()
        }
      }
      candidates = if (multiple) candidates + previews else previews
      if (previews.size != incoming.size) error = "imagePicker.unreadable"
    } finally {
      loading = false
    }
  }

  val paste: () -> Unit = {
    if (!loading) scope.launch {
      val sources = runCatching { clipboardImageSources(Toolkit.getDefaultToolkit().systemClipboard.getContents(null)) }
        .getOrDefault(emptyList())
      if (sources.isEmpty()) error = "imagePicker.clipboardEmpty" else show(sources)
    }
  }
  val browse: () -> Unit = {
    if (!loading) scope.launch {
      loading = true
      try {
        val files = openDesktopFiles(I18n.text("desktopFileAccess.dialog"), IMAGE_EXTENSIONS.toList(), multiple)
        if (files.isNotEmpty()) show(files.map { DesktopImageSource.FromFile(it) })
      } finally {
        loading = false
      }
    }
  }
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    ImagePasteShortcut(paste)
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(
      Modifier.widthIn(min = 280.dp, max = 640.dp).fillMaxWidth()
        .onPreviewKeyEvent { event ->
          when {
            event.type != KeyEventType.KeyDown -> false
            rootFocused && (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
              candidates.takeIf { it.isNotEmpty() && !loading }?.let { onChoose(it.map { it.source }) }
              true
            }
            else -> false
          }
        }.focusRequester(focus).onFocusChanged { rootFocused = it.isFocused }.focusTarget(),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
      DesktopImagePickerPanel(
        candidates,
        loading,
        error?.let { I18n.text(it) },
        onDismiss,
        browse,
        paste,
        { onChoose(candidates.map { it.source }) },
        { removed -> candidates = candidates.filterNot { it === removed } }
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DesktopImagePickerPanel(
  previews: List<DesktopImagePreview>,
  loading: Boolean,
  error: String?,
  onDismiss: () -> Unit,
  onBrowse: () -> Unit,
  onPaste: () -> Unit,
  onChoose: () -> Unit,
  onRemove: (DesktopImagePreview) -> Unit
) {
  Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    ImagePickerHeader(I18n.text("imagePicker.title"), onDismiss)
    Column(
      Modifier.weight(1f, fill = false).mouseScrollable(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text(
        I18n.text("imagePicker.desktopHint"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
          onClick = onBrowse,
          enabled = !loading,
          shapes = ButtonDefaults.shapes(),
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Icon(Icons.Outlined.FolderOpen, null, Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Text(I18n.text("imagePicker.explorer"))
        }
        FilledTonalButton(
          onClick = onPaste,
          enabled = !loading,
          shapes = ButtonDefaults.shapes(),
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Icon(Icons.Outlined.ContentPaste, null, Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Text(I18n.text("imagePicker.paste"))
        }
      }
      if (previews.isEmpty()) {
        Surface(
          Modifier.fillMaxWidth().aspectRatio(1.9f),
          shape = MaterialTheme.shapes.extraLarge,
          color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
          ImagePickerPlaceholder(I18n.text(if (loading) "imagePicker.loading" else "imagePicker.preview"), loading)
        }
      } else {
        Text(I18n.text("imagePicker.count", "count" to previews.size), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          previews.forEach { preview ->
            Surface(
              Modifier.size(160.dp),
              shape = MaterialTheme.shapes.large,
              color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
              Box {
                Image(preview.bitmap, I18n.text("imagePicker.photo"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                IconButton(onClick = { onRemove(preview) }, enabled = !loading, modifier = Modifier.align(Alignment.TopEnd)) {
                  Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(Icons.Outlined.Close, I18n.text("imagePicker.remove"), Modifier.padding(6.dp).size(20.dp))
                  }
                }
              }
            }
          }
        }
      }
      if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
      }
    }
    ImagePickerActions(previews.isNotEmpty() && !loading, onChoose, onDismiss)
  }
}
