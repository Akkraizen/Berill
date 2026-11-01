package dev.akkraizen.berill.plugins.projects.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import kotlinx.serialization.Serializable
@Serializable(with = ProjectStatusSerializer::class)
enum class ProjectStatus { IDEA, IN_PROGRESS, PAUSED, READY }
object ProjectStatusSerializer : LegacyEnumSerializer<ProjectStatus>("projects.status", ProjectStatus.entries,
 { LegacyValues.projectStatus.getValue(it) }, ProjectStatus.IDEA)
