package dev.akkraizen.berill.server.tokens
import java.util.Locale
import java.util.ResourceBundle

enum class TokenValidationMessage {
  INVALID_USERNAME;
  fun text(locale: Locale = Locale.getDefault()): String = ResourceBundle.getBundle("i18n.server", locale, ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT)).getString(name)
}
