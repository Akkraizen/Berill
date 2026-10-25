package dev.akkraizen.berill.core.ui

import dev.akkraizen.berill.api.i18n.Tr

internal object CoreText {

  val back = Tr("core.back")
  val save = Tr("core.save")
  val delete = Tr("core.delete")
  val cancel = Tr("core.cancel")
  val applyColor = Tr("core.applyColor")
  val install = Tr("core.install")
  val search = Tr("core.search")

  val home = Tr("core.home")
  val notes = Tr("core.notes")
  val settings = Tr("core.settings")
  val menu = Tr("core.menu")
  val sections = Tr("core.sections")
  val section = Tr("core.section")

  val sectionLower = Tr("core.sectionLower")

  fun sectionSettingsTitle(section: String) = Tr("core.sectionSettingsTitle", "section" to section)

  val collapsePanel = Tr("core.collapsePanel")
  val expandPanel = Tr("core.expandPanel")
  val sectionUnavailable = Tr("core.sectionUnavailable")

  val goodMorning = Tr("core.goodMorning")
  val goodAfternoon = Tr("core.goodAfternoon")
  val goodEvening = Tr("core.goodEvening")
  val goodNight = Tr("core.goodNight")

  fun homeDate(
    dayMonth: String,
    weekday: String
  ) = Tr("core.homeDate", "dayMonth" to dayMonth, "p1" to weekday.lowercase(), "weekday" to weekday)

  val newNote = Tr("core.newNote")
  val recentNotes = Tr("core.recentNotes")
  val emptyYet = Tr("core.emptyYet")

  val untitled = Tr("core.untitled")
  val selectOrCreateNote = Tr("core.selectOrCreateNote")
  val noNotesYet = Tr("core.noNotesYet")
  val nothingFound = Tr("core.nothingFound")
  val noteFab = Tr("core.noteFab")
  val readMode = Tr("core.readMode")
  val editMode = Tr("core.editMode")
  val deleteNote = Tr("core.deleteNote")
  val titlePlaceholder = Tr("core.titlePlaceholder")
  val bodyPlaceholder = Tr("core.bodyPlaceholder")
  val deleteNoteTitle = Tr("core.deleteNoteTitle")
  val deleteNoteText = Tr("core.deleteNoteText")
  val emptyNote = Tr("core.emptyNote")

  val appearance = Tr("core.appearance")
  val sync = Tr("core.sync")
  val sectionCatalog = Tr("core.sectionCatalog")
  val dataOnDevice = Tr("core.dataOnDevice")

  fun device(id: String) = Tr("core.device", "id" to id)

  val databaseEmpty = Tr("core.databaseEmpty")
  val noPlugin = Tr("core.noPlugin")

  val theme = Tr("core.theme")
  val themeSystem = Tr("core.themeSystem")
  val themeLight = Tr("core.themeLight")
  val themeDark = Tr("core.themeDark")
  val interfaceLanguage = Tr("core.interfaceLanguage")
  val languageSystem = Tr("core.languageSystem")
  val accentColor = Tr("core.accentColor")
  val accentWallpaper = Tr("core.accentWallpaper")
  val customColorLabel = Tr("core.customColorLabel")

  fun customColorSelected(hex: String) = Tr("core.customColorSelected", "hex" to hex)

  val accentGreen = Tr("core.accentGreen")
  val accentEmerald = Tr("core.accentEmerald")
  val accentTeal = Tr("core.accentTeal")
  val accentBlue = Tr("core.accentBlue")
  val accentIndigo = Tr("core.accentIndigo")
  val accentViolet = Tr("core.accentViolet")
  val accentPink = Tr("core.accentPink")
  val accentRed = Tr("core.accentRed")
  val accentOrange = Tr("core.accentOrange")
  val accentAmber = Tr("core.accentAmber")
  val accentGraphite = Tr("core.accentGraphite")

  val noSectionsInstalled = Tr("core.noSectionsInstalled")

  fun sectionSettingsOf(title: String) = Tr("core.sectionSettingsOf", "title" to title)

  fun removeSectionOf(title: String) = Tr("core.removeSectionOf", "title" to title)

  val sectionsHint = Tr("core.sectionsHint")

  fun removeSectionTitle(title: String) = Tr("core.removeSectionTitle", "title" to title)

  val removeSectionText = Tr("core.removeSectionText")
  val allSectionsInstalled = Tr("core.allSectionsInstalled")
  val sectionMissing =
    Tr("core.sectionMissing")
  val sectionNoSettings = Tr("core.sectionNoSettings")
  val sectionDisabledSettingsHint = Tr("core.sectionDisabledSettingsHint")

  val serverUrl = Tr("core.serverUrl")
  val accessToken = Tr("core.accessToken")
  val syncNow = Tr("core.syncNow")
  val syncNotConfigured = Tr("core.syncNotConfigured")
  val syncIdle = Tr("core.syncIdle")
  val syncInProgress = Tr("core.syncInProgress")

  fun syncDone(
    time: String,
    sent: Int,
    received: Int
  ) = Tr("core.syncDone", "time" to time, "sent" to sent, "received" to received)

  fun syncFailed(message: String) =
    Tr("core.syncFailed", "message" to message)

  fun serverResponded(status: String) = Tr("core.serverResponded", "status" to status)

  val errorFallback = Tr("core.errorFallback")

  val e2eTitle = Tr("core.e2eTitle")
  val e2eOffHint = Tr("core.e2eOffHint")
  val e2eOnHint = Tr("core.e2eOnHint")
  val e2eLockedHint = Tr("core.e2eLockedHint")
  val e2ePassword = Tr("core.e2ePassword")
  val e2eRepeat = Tr("core.e2eRepeat")
  val e2eEnable = Tr("core.e2eEnable")
  val e2eUnlock = Tr("core.e2eUnlock")
  val e2eForget = Tr("core.e2eForget")
  val e2eWrongPassword = Tr("core.e2eWrongPassword")
  val e2eShort = Tr("core.e2eShort")
  val e2eWorking = Tr("core.e2eWorking")
  val e2eConfirmTitle = Tr("core.e2eConfirmTitle")
  val e2eConfirmText = Tr("core.e2eConfirmText")
  val e2eForgetText = Tr("core.e2eForgetText")
  val e2eLockedSync = Tr("core.e2eLockedSync")

  fun e2eSkipped(n: Int) =
    Tr("core.e2eSkipped", "n" to n)

  val autoSync = Tr("core.autoSync")
  val autoSyncHint = Tr("core.autoSyncHint")

  fun everyMinutes(n: Int) = Tr("core.everyMinutes", "n" to n)

  val everyHour = Tr("core.everyHour")
  val syncAppearance = Tr("core.syncAppearance")
  val syncAppearanceHint =
    Tr("core.syncAppearanceHint")
  val syncSections = Tr("core.syncSections")
  val syncSectionsHint = Tr("core.syncSectionsHint")
  val syncPluginSettings = Tr("core.syncPluginSettings")
  val syncPluginSettingsHint = Tr("core.syncPluginSettingsHint")
  val data = Tr("core.data")
  val onlyOnThisDevice = Tr("core.onlyOnThisDevice")
  val sectionDisabled = Tr("core.sectionDisabled")
  val syncDataHint = Tr("core.syncDataHint")
}
