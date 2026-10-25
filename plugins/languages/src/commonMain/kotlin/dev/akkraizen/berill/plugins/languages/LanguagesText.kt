package dev.akkraizen.berill.plugins.languages


internal object LanguagesText {

  val title = pluginI18n.tr("languages.title")
  val description = pluginI18n.tr("languages.description")

  val add = pluginI18n.tr("languages.add")
  val save = pluginI18n.tr("languages.save")
  val cancel = pluginI18n.tr("languages.cancel")
  val delete = pluginI18n.tr("languages.delete")
  val language = pluginI18n.tr("languages.language")
  val dictionaryEmpty = pluginI18n.tr("languages.dictionaryEmpty")

  fun sectionLabel(section: String): String =
    when (section) {
      WORDS_SECTION -> pluginI18n.tr("languages.sectionLabel")()
      PHRASES_SECTION -> pluginI18n.tr("languages.sectionLabel.2")()
      else -> section
    }

  val studiedLanguages = pluginI18n.tr("languages.studiedLanguages")
  val listEmpty =
    pluginI18n.tr("languages.listEmpty")

  fun languageSummary(code: String, lessonWord: String, words: Int, lessons: Int) = pluginI18n.tr("languages.languageSummary", "code" to code, "lessonWord" to lessonWord, "words" to words, "lessons" to lessons)

  val codeHint = pluginI18n.tr("languages.codeHint")
  val hiddenRecords = pluginI18n.tr("languages.hiddenRecords")

  fun hiddenCount(code: String, count: Int) = pluginI18n.tr("languages.hiddenCount", "code" to code, "count" to count)

  val restoreLanguage = pluginI18n.tr("languages.restoreLanguage")

  fun purgeTitle(code: String) = pluginI18n.tr("languages.purgeTitle", "code" to code)

  val purgeText = pluginI18n.tr("languages.purgeText")

  fun removeTitle(name: String) = pluginI18n.tr("languages.removeTitle", "name" to name)

  val removeNoRecords = pluginI18n.tr("languages.removeNoRecords")

  fun removeWithRecords(records: Int, code: String) = pluginI18n.tr("languages.removeWithRecords.2", "records" to records, "code" to code, "p2" to pluginI18n.plural("languages.removeWithRecords", records))

  val deleteWithRecords = pluginI18n.tr("languages.deleteWithRecords")
  val remove = pluginI18n.tr("languages.remove")

  val codeRequired = pluginI18n.tr("languages.codeRequired")
  val codeTaken = pluginI18n.tr("languages.codeTaken")
  val newLanguage = pluginI18n.tr("languages.newLanguage")

  fun languageTitle(name: String) = pluginI18n.tr("languages.languageTitle", "name" to name)

  val codeLabel = pluginI18n.tr("languages.codeLabel")
  val selfNameLabel = pluginI18n.tr("languages.selfNameLabel")
  val nativeNameLabel = pluginI18n.tr("languages.nativeNameLabel")

  fun flagLabel(default: String) = pluginI18n.tr("languages.flagLabel", "default" to default)

  val lessonWordLabel = pluginI18n.tr("languages.lessonWordLabel")

  val emptyLanguages = pluginI18n.tr("languages.emptyLanguages")
  val addLanguage = pluginI18n.tr("languages.addLanguage")
  val settings = pluginI18n.tr("languages.settings")
  val newRule = pluginI18n.tr("languages.newRule")
  val statWords = pluginI18n.tr("languages.statWords")
  val statPhrases = pluginI18n.tr("languages.statPhrases")
  val statLessons = pluginI18n.tr("languages.statLessons")
  val statRules = pluginI18n.tr("languages.statRules")
  val wordOfDay = pluginI18n.tr("languages.wordOfDay")
  val lessons = pluginI18n.tr("languages.lessons")
  val dictionary = pluginI18n.tr("languages.dictionary")
  val overview = pluginI18n.tr("languages.overview")

  fun latestLesson(title: String) = pluginI18n.tr("languages.latestLesson", "title" to title)

  val noLessons = pluginI18n.tr("languages.noLessons")
  val lesson = pluginI18n.tr("languages.lesson")
  val rule = pluginI18n.tr("languages.rule")

  val trainer = pluginI18n.tr("languages.trainer")
  val onlyWords = pluginI18n.tr("languages.onlyWords")
  val wordsAndPhrases = pluginI18n.tr("languages.wordsAndPhrases")
  val tapToReveal = pluginI18n.tr("languages.tapToReveal")
  val dontKnow = pluginI18n.tr("languages.dontKnow")
  val know = pluginI18n.tr("languages.know")

  fun dictionaryOf(name: String) = pluginI18n.tr("languages.dictionaryOf", "name" to name)

  val searchPlaceholder = pluginI18n.tr("languages.searchPlaceholder")
  val nothingFound = pluginI18n.tr("languages.nothingFound")
  val newEntry = pluginI18n.tr("languages.newEntry")
  val dictionaryEntry = pluginI18n.tr("languages.dictionaryEntry")
  val pronunciation = pluginI18n.tr("languages.pronunciation")
  val translation = pluginI18n.tr("languages.translation")
  val partOfSpeech = pluginI18n.tr("languages.partOfSpeech")
  val deleteEntryTitle = pluginI18n.tr("languages.deleteEntryTitle")
  val deleteEntryText = pluginI18n.tr("languages.deleteEntryText")

  val lessonDeleted = pluginI18n.tr("languages.lessonDeleted")
  val untitled = pluginI18n.tr("languages.untitled")
  val ruleNoun = pluginI18n.tr("languages.ruleNoun")
  val lessonNoun = pluginI18n.tr("languages.lessonNoun")
  val name = pluginI18n.tr("languages.name")
  val date = pluginI18n.tr("languages.date")
  val lessonNotes = pluginI18n.tr("languages.lessonNotes")
}
