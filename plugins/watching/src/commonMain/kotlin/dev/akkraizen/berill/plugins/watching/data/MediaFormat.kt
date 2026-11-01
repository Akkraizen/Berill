package dev.akkraizen.berill.plugins.watching.data
import dev.akkraizen.berill.plugins.watching.i18n.pluginI18n
enum class MediaFormat(val translationKey: String) {
 MOVIE("watching.formatLabel"),
 SPECIAL("watching.formatLabel.2"),
 TV_SPECIAL("watching.formatLabel.3"),
 MUSIC("watching.formatLabel.4"),
 VIDEO("watching.formatLabel.5"),
 SERIES("watching.formatLabel.6"),
 MINI_SERIES("watching.formatLabel.7"),
 TV_SHOW("watching.formatLabel.8");
 val wire: String get() = pluginI18n.tr("legacy.MediaFormat.$name").get("ru")
}
