package dev.akkraizen.berill.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.widgets.WidgetProvider

interface BerillPlugin {
  val id: String

  val title: String

  val icon: ImageVector

  val order: Int
    get() = 100

  val description: String
    get() = ""

  val installedByDefault: Boolean
    get() = false

  val hasSettings: Boolean
    get() = false

  @Composable
  fun Settings(ctx: PluginContext) {
  }

  val collections: List<String>
    get() = emptyList()

  val pages: List<PluginPage>
    get() = emptyList()

  @Composable
  fun Screen(ctx: PluginContext) {
  }

  fun homeCards(ctx: PluginContext): List<HomeCard> = emptyList()

  fun quickActions(ctx: PluginContext): List<QuickAction> = emptyList()

  fun widgets(ctx: PluginContext): List<WidgetProvider> = emptyList()

  val permissions: PluginPermissions
    get() = PluginPermissions.None

  fun provides(ctx: PluginContext): List<Capability> = emptyList()
}

class PluginPage(val id: String, val title: Tr, val icon: ImageVector, val content: @Composable (PluginContext) -> Unit)

class HomeCard(val id: String, val title: Tr, val order: Int = 100, val content: @Composable () -> Unit)

class QuickAction(val id: String, val label: Tr, val icon: ImageVector, val run: () -> Unit)
