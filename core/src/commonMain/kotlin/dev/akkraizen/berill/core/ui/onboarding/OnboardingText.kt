package dev.akkraizen.berill.core.ui.onboarding

import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.i18n.Tr

internal object OnboardingText {
  val welcomeTitle = Tr("onboarding.welcomeTitle")
  val welcomeText = Tr("onboarding.welcomeText")
  val language = Tr("onboarding.language")
  val system = Tr("onboarding.system")

  val themeTitle = Tr("onboarding.themeTitle")
  val themeText = Tr("onboarding.themeText")

  val accentTitle = Tr("onboarding.accentTitle")
  val accentText = Tr("onboarding.accentText")
  val wallpaper = Tr("onboarding.wallpaper")

  val sectionsTitle = Tr("onboarding.sectionsTitle")
  val sectionsText = Tr("onboarding.sectionsText")
  val all = Tr("onboarding.all")
  val none = Tr("onboarding.none")

  fun enabledCount(n: Int) =
    I18n.plural("onboarding.enabledCount", n)

  val doneTitle = Tr("onboarding.doneTitle")
  val doneText = Tr("onboarding.doneText")
  val open = Tr("onboarding.open")

  val start = Tr("onboarding.start")
  val next = Tr("onboarding.next")
  val back = Tr("onboarding.back")
  val skip = Tr("onboarding.skip")

  fun step(n: Int, total: Int) = Tr("onboarding.step", "n" to n, "total" to total)

  val replay = Tr("onboarding.replay")
}
