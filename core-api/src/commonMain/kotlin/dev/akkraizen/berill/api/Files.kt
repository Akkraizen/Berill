package dev.akkraizen.berill.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class PickedFile(val name: String, val bytes: ByteArray, val mimeType: String)

interface FileAccess {
  val supportsImageDrop: Boolean get() = false

  @Composable
  fun imageDropTarget(enabled: Boolean = true, onDrop: (List<PickedFile>) -> Unit): Modifier =
    Modifier

  suspend fun storeAttachment(bytes: ByteArray): String? = null
  suspend fun loadAttachment(hash: String): ByteArray? = null

  suspend fun saveFile(name: String, bytes: ByteArray, mimeType: String): Boolean = false

  suspend fun pickImage(maxSide: Int = 1600): PickedFile?

  suspend fun pickImages(maxSide: Int = 1600): List<PickedFile> = listOfNotNull(pickImage(maxSide))

  suspend fun pickFiles(
    extensions: List<String>,
    maxBytes: Int = 20 * 1024 * 1024
  ): List<PickedFile> =
    listOfNotNull(pickFile(extensions, maxBytes))

  suspend fun pickFile(extensions: List<String>, maxBytes: Int = 20 * 1024 * 1024): PickedFile? =
    null

  /**
   * Asks for a folder that later writes may use without asking again (automatic export). Returns a
   * reference to store — a path on desktop, a document tree URI on Android — or null.
   */
  suspend fun pickFolder(): String? = null

  /** Short, readable name of a folder reference from [pickFolder]. */
  fun folderLabel(folder: String): String = folder

  suspend fun writeToFolder(folder: String, name: String, bytes: ByteArray): Boolean = false

  suspend fun listFolder(folder: String): List<String> = emptyList()

  suspend fun deleteFromFolder(folder: String, name: String): Boolean = false
}

object NoFileAccess : FileAccess {
  override suspend fun pickImage(maxSide: Int): PickedFile? = null
}
