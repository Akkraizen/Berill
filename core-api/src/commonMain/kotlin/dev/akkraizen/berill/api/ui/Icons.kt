package dev.akkraizen.berill.api.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

object BerillIcons {
  val Back: ImageVector
    get() = Icons.AutoMirrored.Filled.ArrowBack

  val Forward: ImageVector
    get() = Icons.AutoMirrored.Filled.ArrowForward

  val Prev: ImageVector
    get() = Icons.Outlined.ChevronLeft

  val Next: ImageVector
    get() = Icons.Outlined.ChevronRight

  val Add: ImageVector
    get() = Icons.Outlined.Add

  val Delete: ImageVector
    get() = Icons.Outlined.Delete

  val Edit: ImageVector
    get() = Icons.Outlined.Edit

  val Check: ImageVector
    get() = Icons.Outlined.Check

  val Close: ImageVector
    get() = Icons.Outlined.Close

  val Dropdown: ImageVector
    get() = Icons.Outlined.ArrowDropDown

  val Star: ImageVector
    get() = Icons.Filled.Star

  val StarOutline: ImageVector
    get() = Icons.Outlined.StarBorder

  val catalog: Map<String, ImageVector> by lazy {
    linkedMapOf(
      "task" to Icons.Outlined.TaskAlt,
      "fire" to Icons.Outlined.LocalFireDepartment,
      "code" to Icons.Outlined.Code,
      "translate" to Icons.Outlined.Translate,
      "book" to Icons.Outlined.MenuBook,
      "notebook" to Icons.Outlined.Book,
      "fitness" to Icons.Outlined.FitnessCenter,
      "run" to Icons.Outlined.DirectionsRun,
      "meditation" to Icons.Outlined.SelfImprovement,
      "puzzle" to Icons.Outlined.Extension,
      "brain" to Icons.Outlined.Psychology,
      "language" to Icons.Outlined.Language,
      "music" to Icons.Outlined.MusicNote,
      "art" to Icons.Outlined.Brush,
      "water" to Icons.Outlined.WaterDrop,
      "sleep" to Icons.Outlined.Bedtime,
      "spa" to Icons.Outlined.Spa,
      "food" to Icons.Outlined.Restaurant,
      "school" to Icons.Outlined.School,
      "work" to Icons.Outlined.Work,
      "heart" to Icons.Outlined.Favorite,
      "idea" to Icons.Outlined.Lightbulb,
      "timer" to Icons.Outlined.Timer,
      "savings" to Icons.Outlined.Savings
    )
  }

  fun byKey(key: String?, fallback: ImageVector = Icons.Outlined.TaskAlt): ImageVector = catalog[key] ?: fallback
}
