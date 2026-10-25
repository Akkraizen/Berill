package dev.akkraizen.berill.core.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.core.ui.CoreText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

sealed interface Destination {
  data object Home : Destination

  data object Notes : Destination

  data class Note(val id: String) : Destination

  data class Plugin(val id: String, val route: String? = null, val page: String? = null) : Destination

  data class PluginSettings(val id: String) : Destination

  data object Settings : Destination

  data class SettingsSection(val id: String) : Destination
}

enum class Tab(val icon: ImageVector, private val tr: Tr, val root: Destination) {
  Home(Icons.Outlined.Home, CoreText.home, Destination.Home),
  Notes(Icons.Outlined.Description, CoreText.notes, Destination.Notes),
  Settings(Icons.Outlined.Settings, CoreText.settings, Destination.Settings),
  ;

  val title: String
    get() = tr()
}

val Destination.tab: Tab?
  get() =
    when (this) {
      Destination.Home -> Tab.Home
      Destination.Notes,
      is Destination.Note -> Tab.Notes

      Destination.Settings,
      is Destination.SettingsSection,
      is Destination.PluginSettings -> Tab.Settings

      is Destination.Plugin -> null
    }

class AppNavigator(private val newNoteId: () -> String) {
  private val _stack = MutableStateFlow<List<Destination>>(listOf(Destination.Home))
  val stack: StateFlow<List<Destination>> = _stack

  val current: Destination
    get() = _stack.value.last()

  fun selectTab(tab: Tab) {
    _stack.value = if (tab == Tab.Home) listOf(Destination.Home) else listOf(Destination.Home, tab.root)
  }

  fun push(destination: Destination) = _stack.update { if (it.last() == destination) it else it + destination }

  fun back(): Boolean {
    val s = _stack.value
    if (s.size <= 1) return false
    _stack.value = s.dropLast(1)
    return true
  }

  fun openNote(id: String?) {
    val target = Destination.Note(id ?: newNoteId())
    _stack.update { if (it.last() is Destination.Note) it.dropLast(1) + target else it + target }
  }

  fun openPlugin(id: String, route: String? = null, page: String? = null) = push(Destination.Plugin(id, route, page))

  fun openPluginSettings(pluginId: String) = push(Destination.PluginSettings(pluginId))

  fun openSettings(section: String) = push(Destination.SettingsSection(section))

  fun openSection(pluginId: String, page: String? = null) {
    _stack.value = listOf(Destination.Home, Destination.Plugin(pluginId, page = page))
  }
}
