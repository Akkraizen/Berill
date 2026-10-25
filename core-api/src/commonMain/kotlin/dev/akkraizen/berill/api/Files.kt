package dev.akkraizen.berill.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class PickedFile(val name: String, val bytes: ByteArray, val mimeType: String)

interface FileAccess {
  val supportsImageDrop: Boolean get() = false

  @Composable
  fun imageDropTarget(enabled: Boolean = true, onDrop: (List<PickedFile>) -> Unit): Modifier =
    Modifier

  suspend fun pickImage(maxSide: Int = 1600): PickedFile?

  suspend fun pickImages(maxSide: Int = 1600): List<PickedFile> = listOfNotNull(pickImage(maxSide))

  suspend fun pickFiles(
    extensions: List<String>,
    maxBytes: Int = 20 * 1024 * 1024
  ): List<PickedFile> =
    listOfNotNull(pickFile(extensions, maxBytes))

  suspend fun pickFile(extensions: List<String>, maxBytes: Int = 20 * 1024 * 1024): PickedFile? =
    null
}

object NoFileAccess : FileAccess {
  override suspend fun pickImage(maxSide: Int): PickedFile? = null
}
