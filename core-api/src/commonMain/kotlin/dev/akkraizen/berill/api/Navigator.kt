package dev.akkraizen.berill.api

import kotlinx.coroutines.flow.StateFlow

interface Navigator {
  val route: StateFlow<String?>

  val page: StateFlow<String?>

  fun openPage(pageId: String)

  fun open(route: String?)

  fun back(): Boolean

  fun openPlugin(pluginId: String, route: String? = null, page: String? = null)

  fun openNote(noteId: String?)

  fun openSettings()
}
