package dev.akkraizen.berill.core.update

import dev.akkraizen.berill.core.BuildInfo
import dev.akkraizen.berill.core.data.LocalStore
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.sync.clientPlatform
import dev.akkraizen.berill.core.sync.createHttpClient
import dev.akkraizen.berill.protocol.LatestRelease
import dev.akkraizen.berill.protocol.compareVersions
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppUpdate(val version: String, val url: String?)

sealed interface UpdateCheck {
  data object Unknown : UpdateCheck

  data object Checking : UpdateCheck

  data object UpToDate : UpdateCheck

  data class Available(val update: AppUpdate) : UpdateCheck

  data class Failed(val message: String) : UpdateCheck
}

class AppUpdates(private val store: LocalStore) {
  private val http = createHttpClient()
  private val _state = MutableStateFlow<UpdateCheck>(UpdateCheck.Unknown)
  val state: StateFlow<UpdateCheck> = _state

  fun serverUrl(): String = (store.meta(KEY_URL) ?: BuildInfo.UPDATES_URL).trim().trimEnd('/')

  fun setServerUrl(url: String) {
    store.setMeta(KEY_URL, url.trim().trimEnd('/'))
    _state.value = UpdateCheck.Unknown
  }

  fun start(scope: CoroutineScope) {
    scope.launch {
      delay(FIRST_CHECK_DELAY)
      while (true) {
        check()
        delay(CHECK_INTERVAL)
      }
    }
  }

  suspend fun check() {
    val url = serverUrl().takeIf { it.isNotEmpty() } ?: return
    _state.value = UpdateCheck.Checking
    _state.value = runCatching {
      val response = http.get("$url/api/release")
      when {
        response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.NotFound -> UpdateCheck.UpToDate
        !response.status.isSuccess() -> UpdateCheck.Failed(response.status.toString())
        else -> {
          val latest: LatestRelease = response.body()
          if (compareVersions(latest.version, BuildInfo.VERSION_NAME) > 0) {
            val path = when (clientPlatform) {
              "android" -> latest.android
              "macos" -> latest.macos
              else -> latest.windows
            }
            AppLog.info(TAG, "update available: ${latest.version}")
            UpdateCheck.Available(AppUpdate(latest.version, path?.let { "$url$it" }))
          } else {
            UpdateCheck.UpToDate
          }
        }
      }
    }.getOrElse { UpdateCheck.Failed(it.message ?: it::class.simpleName.orEmpty()) }
  }

  private companion object {
    const val TAG = "update"
    const val KEY_URL = "updates_url"
    const val FIRST_CHECK_DELAY = 15_000L
    const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L
  }
}
