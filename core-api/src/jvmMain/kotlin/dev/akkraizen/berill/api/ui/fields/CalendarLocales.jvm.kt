package dev.akkraizen.berill.api.ui.fields

import androidx.compose.material3.CalendarLocale
import java.util.Locale

internal actual fun calendarLocale(code: String): CalendarLocale = Locale.forLanguageTag(code)
