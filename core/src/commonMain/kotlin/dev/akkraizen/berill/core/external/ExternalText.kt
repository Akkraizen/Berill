package dev.akkraizen.berill.core.external

import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.i18n.Tr

internal object ExternalText {
  val notStarted = Tr("external.notStarted")
  val notResponding =
    Tr("external.notResponding")

  fun startFailed(reason: String) = Tr("external.startFailed", "reason" to reason)

  fun loadFailed(reason: String) = Tr("external.loadFailed", "reason" to reason)

  fun crashed(reason: String) = Tr("external.crashed", "reason" to reason)

  fun tooManyCrashes(reason: String) =
    Tr("external.tooManyCrashes", "reason" to reason)

  val retry = Tr("external.retry")
  val loading = Tr("external.loading")

  val sectionTitle = Tr("external.sectionTitle")
  val sectionHint = Tr("external.sectionHint")
  val none = Tr("external.none")
  val installFromFile = Tr("external.installFromFile")
  val installTitle = Tr("external.installTitle")
  val install = Tr("external.install")
  val cancel = Tr("external.cancel")
  val close = Tr("external.close")
  val remove = Tr("external.remove")

  fun version(v: String, author: String) = Tr("external.version", "v" to v, "p1" to if (author.isNotBlank()) " · $author" else "")

  val permissions = Tr("external.permissions")
  val noNetwork = Tr("external.noNetwork")

  fun network(hosts: String) = Tr("external.network", "hosts" to hosts)

  fun collections(n: Int) =
    Tr("external.collections.3", "p0" to I18n.plural("external.collections", n), "p1" to I18n.plural("external.collections.2", n))

  fun pages(n: Int) = Tr("external.pages", "n" to n)

  val badPackage = Tr("external.badPackage")

  fun installed(name: String) =
    Tr("external.installed", "name" to name)

  val restartNeeded = Tr("external.restartNeeded")

  fun removeTitle(name: String) = Tr("external.removeTitle", "name" to name)

  val removeText = Tr("external.removeText")

  fun rejected(id: String) = Tr("external.rejected", "id" to id)

  val notSupported =
    Tr("external.notSupported")
  val running = Tr("external.running")
}
