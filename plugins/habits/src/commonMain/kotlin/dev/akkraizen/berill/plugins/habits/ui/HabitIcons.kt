package dev.akkraizen.berill.plugins.habits.ui

import androidx.compose.ui.graphics.vector.ImageVector
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.plugins.habits.data.HabitDef
import dev.akkraizen.berill.plugins.habits.data.guessIconKey

fun habitIcon(id: String, def: HabitDef): ImageVector = BerillIcons.byKey(def.icon ?: guessIconKey(id))
