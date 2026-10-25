package dev.akkraizen.berill.core

import com.sun.jna.Platform
import dev.akkraizen.berill.core.files.WindowsOpenFileDialog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class WindowsFileDialogTest {
  @Test
  fun enablesMultipleSelectionOnlyWhenRequested() = runBlocking {
    if (!Platform.isWindows()) return@runBlocking
    withContext(Dispatchers.IO) {
      for (multiple in listOf(false, true)) {
        WindowsOpenFileDialog("Images", listOf("png", "jpg"), multiple).use { dialog ->
          assertEquals(
            if (multiple) WindowsOpenFileDialog.FOS_ALLOWMULTISELECT else 0,
            dialog.options and WindowsOpenFileDialog.FOS_ALLOWMULTISELECT
          )
        }
      }
    }
  }

  @Test
  fun createsModernShellDialogWithUnicodeTitleAndImageFilters() = runBlocking {
    if (!Platform.isWindows()) return@runBlocking
    withContext(Dispatchers.IO) {
      WindowsOpenFileDialog("Выбор изображения", listOf("png", "jpg", "jpeg", "gif", "bmp")).use { dialog ->
        val required = WindowsOpenFileDialog.FOS_FORCEFILESYSTEM or WindowsOpenFileDialog.FOS_PATHMUSTEXIST or
          WindowsOpenFileDialog.FOS_FILEMUSTEXIST
        assertEquals(required, dialog.options and required)
      }
    }
  }

  @Test
  fun supportsUnrestrictedAndPluginPackageFiltersAndReleasesComResources() = runBlocking {
    if (!Platform.isWindows()) return@runBlocking
    withContext(Dispatchers.IO) {
      for (extensions in listOf(emptyList(), listOf("berill-plugin", "zip"))) {
        val dialog = WindowsOpenFileDialog("Choose file", extensions)
        dialog.close()
        dialog.close()
        assertFailsWith<IllegalStateException> { dialog.options }
      }
    }
  }
}
