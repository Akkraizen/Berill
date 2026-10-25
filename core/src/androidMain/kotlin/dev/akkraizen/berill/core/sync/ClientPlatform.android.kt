package dev.akkraizen.berill.core.sync

import android.os.Build

internal actual fun clientDeviceName(): String {
  val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
  val model = Build.MODEL.orEmpty()
  return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
}

internal actual val clientPlatform: String = "android"
