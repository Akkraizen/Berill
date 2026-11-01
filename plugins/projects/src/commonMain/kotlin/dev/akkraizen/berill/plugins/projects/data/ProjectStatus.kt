package dev.akkraizen.berill.plugins.projects.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import dev.akkraizen.berill.plugins.projects.i18n.pluginI18n
import kotlinx.serialization.Serializable
@Serializable(with = ProjectStatusSerializer::class)
enum class ProjectStatus { IDEA, IN_PROGRESS, PAUSED, READY }
object ProjectStatusSerializer : LegacyEnumSerializer<ProjectStatus>("projects.status", ProjectStatus.entries,
 { pluginI18n.tr("legacy.ProjectStatus.${it.name}").get("ru") }, ProjectStatus.IDEA)
