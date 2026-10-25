package dev.akkraizen.berill.core.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.akkraizen.berill.api.FileAccess
import dev.akkraizen.berill.api.PickedFile
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object DocumentPicker {
  internal var launch: (() -> Unit)? = null
  internal var pending: ((Uri?) -> Unit)? = null

  fun register(activity: ComponentActivity) {
    val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      pending?.invoke(uri)
      pending = null
    }
    launch = { launcher.launch(arrayOf("*/*")) }
  }
}

internal class AndroidFileAccess(private val context: Context) : FileAccess {
  private val imageMutex = Mutex()

  override suspend fun pickImage(maxSide: Int): PickedFile? = imageMutex.withLock {
    require(maxSide > 0)
    val uri = withContext(Dispatchers.Main) {
      val launch = ImagePicker.launch ?: return@withContext null
      suspendCancellableCoroutine<Uri?> { cont ->
        val callback: (Uri?) -> Unit = { if (cont.isActive) cont.resume(it) }
        ImagePicker.pending = callback
        cont.invokeOnCancellation {
          Handler(Looper.getMainLooper()).post {
            if (ImagePicker.pending === callback) ImagePicker.finish(null)
          }
        }
        launch()
      }
    } ?: return@withLock null
    return@withLock withContext(Dispatchers.IO) {
      val bitmap = runCatching { decode(uri, maxSide) }.getOrNull() ?: return@withContext null
      val out = ByteArrayOutputStream()
      try {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
      } finally {
        bitmap.recycle()
      }
      PickedFile("photo.jpg", out.toByteArray(), "image/jpeg")
    }
  }

  override suspend fun pickFile(extensions: List<String>, maxBytes: Int): PickedFile? {
    val uri = withContext(Dispatchers.Main) {
      val launch = DocumentPicker.launch ?: return@withContext null
      suspendCancellableCoroutine<Uri?> { cont ->
        DocumentPicker.pending = { cont.resume(it) }
        cont.invokeOnCancellation { DocumentPicker.pending = null }
        launch()
      }
    } ?: return null
    return withContext(Dispatchers.IO) {
      val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
      val allowed = extensions.map { it.lowercase() }
      if (allowed.isNotEmpty() &&
        '.' in name &&
        name.substringAfterLast('.').lowercase() !in allowed
      )
        return@withContext null
      val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
          val n = input.read(buffer)
          if (n < 0) break
          out.write(buffer, 0, n)
          if (out.size() > maxBytes) return@withContext null
        }
        out.toByteArray()
      } ?: return@withContext null
      PickedFile(name, bytes, "application/octet-stream")
    }
  }

  private fun decode(uri: Uri, maxSide: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val source = ImageDecoder.createSource(context.contentResolver, uri)
      return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val scale = minOf(1.0, maxSide.toDouble() / max(info.size.width, info.size.height))
        decoder.setTargetSize(
          (info.size.width * scale).roundToInt().coerceAtLeast(1),
          (info.size.height * scale).roundToInt().coerceAtLeast(1)
        )
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      }
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
      ?: return null
    val scale = minOf(1.0, maxSide.toDouble() / max(decoded.width, decoded.height))
    if (scale >= 1.0) return decoded
    return Bitmap.createScaledBitmap(
      decoded,
      (decoded.width * scale).roundToInt().coerceAtLeast(1),
      (decoded.height * scale).roundToInt().coerceAtLeast(1),
      true
    ).also { if (it !== decoded) decoded.recycle() }
  }
}
