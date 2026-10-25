package dev.akkraizen.berill.core.files

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.ui.media.ImagePickerActions
import dev.akkraizen.berill.core.ui.media.ImagePickerHeader
import dev.akkraizen.berill.core.ui.media.ImagePickerPlaceholder
import dev.akkraizen.berill.core.ui.media.ImagePickerTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImagePicker {
  internal var launch: (() -> Unit)? = null
  internal var pending: ((Uri?) -> Unit)? = null
  private var visible by mutableStateOf(false)
  private var revision by mutableStateOf(0)
  private var requestId = 0L
  private var systemRequestId: Long? = null
  private var systemPick: (() -> Unit)? = null
  private var permissions: (() -> Unit)? = null

  fun register(activity: ComponentActivity) {
    val picker = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      val request = systemRequestId
      systemRequestId = null
      if (request == requestId && uri != null && pending != null) finish(uri)
    }
    val permission = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { revision++ }
    launch = { requestId++; visible = true; revision++ }
    systemPick = {
      systemRequestId = requestId
      picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    permissions = { permission.launch(photoPermissions()) }
    activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) revision++
      if (event == Lifecycle.Event.ON_DESTROY) {
        finish(null)
        launch = null
        systemPick = null
        permissions = null
      }
    })
  }

  internal fun finish(uri: Uri?) {
    visible = false
    val callback = pending
    pending = null
    callback?.invoke(uri)
  }

  @Composable
  fun Content() {
    if (!visible) return
    GallerySheet(
      revision,
      onDismiss = { finish(null) },
      onChoose = { finish(it) },
      onSystemPick = { systemPick?.invoke() },
      onPermissions = { permissions?.invoke() }
    )
  }
}

private fun photoPermissions(): Array<String> = when {
  Build.VERSION.SDK_INT >= 34 -> arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
  )
  Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
  else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.hasPhotoAccess(): Boolean =
  photoPermissions().any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

private fun Context.hasFullPhotoAccess(): Boolean =
  checkSelfPermission(
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
  ) == PackageManager.PERMISSION_GRANTED

private const val PHOTO_PAGE_SIZE = 90

private fun recentPhotos(context: Context, offset: Int): List<Uri> {
  val result = mutableListOf<Uri>()
  context.contentResolver.query(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    arrayOf(MediaStore.Images.Media._ID),
    null,
    null,
    "date_added DESC, _id DESC"
  )?.use { cursor ->
    if (offset > 0 && !cursor.moveToPosition(offset - 1)) return result
    while (result.size < PHOTO_PAGE_SIZE && cursor.moveToNext()) {
      result += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
    }
  }
  return result
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GallerySheet(
  refresh: Int,
  onDismiss: () -> Unit,
  onChoose: (Uri) -> Unit,
  onSystemPick: () -> Unit,
  onPermissions: () -> Unit
) {
  val context = LocalContext.current
  val accessible = remember(refresh) { context.hasPhotoAccess() }
  val fullAccess = remember(refresh) { context.hasFullPhotoAccess() }
  var photos by remember(refresh) { mutableStateOf(emptyList<Uri>()) }
  var loading by remember(refresh) { mutableStateOf(accessible) }
  var more by remember(refresh) { mutableStateOf(accessible) }
  var failed by remember(refresh) { mutableStateOf(false) }
  var selected by remember(refresh) { mutableStateOf<Uri?>(null) }
  val grid = rememberLazyGridState()
  val lastVisible = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
  LaunchedEffect(refresh, accessible, lastVisible, photos.size) {
    if (!accessible || !more || (photos.isNotEmpty() && lastVisible < photos.size - 18)) return@LaunchedEffect
    loading = true
    val page = withContext(Dispatchers.IO) { runCatching { recentPhotos(context, photos.size) } }
    page.onSuccess { photos = (photos + it).distinct(); more = it.size == PHOTO_PAGE_SIZE }
      .onFailure { failed = true; more = false }
    loading = false
  }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberBottomSheetState(
      initialValue = SheetValue.Hidden,
      enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    ),
    shape = MaterialTheme.shapes.extraLarge,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
  ) {
    Column(
      Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp * 0.82f).dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ImagePickerHeader(I18n.text("imagePicker.gallery"), onDismiss)
        FilledTonalButton(
          onClick = onSystemPick,
          shapes = ButtonDefaults.shapes(),
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
          Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Text(I18n.text("imagePicker.system"))
        }
        if (!fullAccess) {
          Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
          ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                I18n.text(if (accessible) "imagePicker.partialAccess" else "imagePicker.permissionHint"),
                style = MaterialTheme.typography.bodyMedium
              )
              TextButton(onClick = onPermissions) {
                Text(I18n.text(if (accessible) "imagePicker.manageAccess" else "imagePicker.allowPhotos"))
              }
            }
          }
        }
      }
      Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(96.dp),
          state = grid,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(photos, key = { it.toString() }) { uri ->
            ImagePickerTile(selected = uri == selected, onClick = { selected = uri }, modifier = Modifier.aspectRatio(1f)) {
              GalleryThumbnail(uri, Modifier.fillMaxSize())
            }
          }
        }
        if (photos.isEmpty()) {
          if (loading) ImagePickerPlaceholder(I18n.text("imagePicker.loading"), loading = true)
          else if (accessible) {
            ImagePickerPlaceholder(I18n.text(if (failed) "imagePicker.galleryError" else "imagePicker.galleryEmpty"))
          }
        }
      }
      ImagePickerActions(
        enabled = selected != null,
        onChoose = { selected?.let(onChoose) },
        onDismiss = onDismiss,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
      )
    }
  }
}

@Composable
private fun GalleryThumbnail(uri: Uri, modifier: Modifier) {
  val context = LocalContext.current
  val bitmap by produceState<Bitmap?>(null, uri) {
    value = withContext(Dispatchers.IO) {
      runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
          context.contentResolver.loadThumbnail(uri, Size(240, 240), null)
        } else {
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
          var sample = 1
          while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 240) sample *= 2
          context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
          }
        }
      }.getOrNull()
    }
  }
  Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
    bitmap?.let {
      Image(it.asImageBitmap(), I18n.text("imagePicker.photo"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } ?: Icon(Icons.Outlined.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
