package dev.akkraizen.berill.plugins.study.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import dev.akkraizen.berill.plugins.study.i18n.pluginI18n
import kotlinx.serialization.Serializable
@Serializable(with = TaskStatusSerializer::class)
enum class TaskStatus { TODO, DOING, SUBMITTED }
object TaskStatusSerializer : LegacyEnumSerializer<TaskStatus>("study.status", TaskStatus.entries,
 { pluginI18n.tr("legacy.TaskStatus.${it.name}").get("ru") }, TaskStatus.TODO)

/** Known controls in an extensible, user-editable text field. */
enum class SubjectControl { EXAM, CREDIT;
 val wire: String get() = pluginI18n.list("legacy.SubjectControl.$name").first()
 fun matches(value: String?): Boolean = value != null && (value == name || pluginI18n.list("legacy.SubjectControl.$name").any { value.contains(it, ignoreCase = true) })
}
