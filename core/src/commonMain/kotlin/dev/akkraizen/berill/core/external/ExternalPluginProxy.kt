package dev.akkraizen.berill.core.external

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPage
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.core.plugin.PluginHost
import dev.akkraizen.berill.protocol.Collections
import dev.akkraizen.berill.sdk.i18n.TranslationCatalog
import dev.akkraizen.berill.sdk.protocol.PluginManifest
import dev.akkraizen.berill.sdk.protocol.Target
import dev.akkraizen.berill.sdk.protocol.TargetKind
import dev.akkraizen.berill.sdk.protocol.problems
import kotlinx.coroutines.CoroutineScope

class ExternalPluginProxy(
  val manifest: PluginManifest,
  private val launcher: RunnerLauncher,
  private val scope: CoroutineScope
) : BerillPlugin {
  private val catalog = TranslationCatalog.fromLocalizedMessages(buildMap {
    manifest.pages.forEach { put("pages.${it.id}", it.title.translations) }
    manifest.homeCards.forEach { put("cards.${it.id}", it.title.translations) }
  })
  override val id: String = manifest.id
  override val title: String
    get() = manifest.name.get(I18n.language.code)

  override val description: String
    get() = manifest.description.get(I18n.language.code)

  override val icon: ImageVector = remoteIcon(manifest.icon) ?: Icons.Outlined.Extension
  override val order: Int = 500
  override val installedByDefault: Boolean = true
  override val collections: List<String> = manifest.collections
  override val permissions = PluginPermissions(network = manifest.permissions.network)
  override val hasSettings: Boolean = manifest.hasSettings

  private var client: ExternalPluginClient? = null

  fun client(ctx: PluginContext): ExternalPluginClient = client ?: ExternalPluginClient(manifest, launcher, ctx, scope).also { client = it }

  val activeClient: ExternalPluginClient?
    get() = client

  override val pages: List<PluginPage> = manifest.pages.map { page ->
    PluginPage(page.id, Tr("pages.${page.id}", catalog = catalog), remoteIcon(page.icon) ?: icon) { ctx ->
      RemotePage(client(ctx), ctx, page.id)
    }
  }

  override fun homeCards(ctx: PluginContext): List<HomeCard> =
    manifest.homeCards.map { card ->
      HomeCard(card.id, Tr("cards.${card.id}", catalog = catalog), order = 300) {
        RemoteContent(client(ctx), Target(TargetKind.HomeCard, card.id))
      }
    }

  @Composable
  override fun Settings(ctx: PluginContext) {
    RemoteContent(client(ctx), Target(TargetKind.Settings, "settings"))
  }
}

class ExternalPlugins(
  val platform: ExternalPluginPlatform?,
  val loaded: List<ExternalPluginProxy>,
  val rejected: Map<String, List<String>>
) {
  companion object {
    val None = ExternalPlugins(null, emptyList(), emptyMap())
  }
}

fun loadExternalPlugins(
  platform: ExternalPluginPlatform?,
  builtIn: List<BerillPlugin>,
  scope: CoroutineScope
): ExternalPlugins {
  if (platform == null) return ExternalPlugins.None
  val takenIds = builtIn.map { it.id }.toMutableSet().apply { addAll(PluginHost.RESERVED) }
  val takenCollections = (builtIn.flatMap { it.collections } + Collections.NOTES + Collections.SETTINGS).toMutableSet()
  val loaded = mutableListOf<ExternalPluginProxy>()
  val rejected = linkedMapOf<String, List<String>>()
  val manifests = runCatching { platform.installed() }
    .getOrElse {
      return ExternalPlugins(platform, emptyList(), mapOf("plugins" to listOf(it.message ?: "error")))
    }
  for (m in manifests) {
    val problems = m.problems().toMutableList()
    if (m.id in takenIds) problems += "id `${m.id}` уже занят"
    m.collections
      .filter { it in takenCollections }
      .forEach { problems += "коллекция `$it` уже занята" }
    if (problems.isNotEmpty()) {
      rejected[m.id] = problems
      continue
    }
    takenIds += m.id
    takenCollections += m.collections
    loaded += ExternalPluginProxy(m, platform.launcher, scope)
  }
  return ExternalPlugins(platform, loaded, rejected)
}
