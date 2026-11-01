package dev.akkraizen.berill.plugins.study.data
import dev.akkraizen.berill.api.i18n.LegacyEnumSerializer
import kotlinx.serialization.Serializable
@Serializable(with = TaskStatusSerializer::class)
enum class TaskStatus { TODO, DOING, SUBMITTED }
object TaskStatusSerializer : LegacyEnumSerializer<TaskStatus>("study.status", TaskStatus.entries,
 { LegacyValues.taskStatus.getValue(it) }, TaskStatus.TODO)

/** Known controls in an extensible, user-editable text field. */
enum class SubjectControl { EXAM, CREDIT;
 val wire: String get() = LegacyValues.subjectControl.getValue(this).first()
 fun matches(value: String?): Boolean = value != null && (value == name || LegacyValues.subjectControl.getValue(this).any { value.contains(it, ignoreCase = true) })
}
