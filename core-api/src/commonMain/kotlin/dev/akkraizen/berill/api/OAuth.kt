package dev.akkraizen.berill.api

interface OAuthRedirects {
  suspend fun receive(open: (redirectUri: String) -> Unit): Map<String, String>
}

object NoOAuthRedirects : OAuthRedirects {
  override suspend fun receive(open: (redirectUri: String) -> Unit): Map<String, String> =
    throw UnsupportedOperationException("Browser sign-in is not available")
}
