package dev.akkraizen.berill.server.tokens
import java.util.Locale
import kotlin.test.*
class TokenValidationMessageTest {
 @Test fun validationIsLocalizedWithEnglishFallback() {
  assertTrue(TokenValidationMessage.INVALID_USERNAME.text(Locale.forLanguageTag("ru")).startsWith("Имя пользователя"))
  assertTrue(TokenValidationMessage.INVALID_USERNAME.text(Locale.ENGLISH).startsWith("Username"))
  assertTrue(TokenValidationMessage.INVALID_USERNAME.text(Locale.JAPANESE).startsWith("Username"))
 }
}
