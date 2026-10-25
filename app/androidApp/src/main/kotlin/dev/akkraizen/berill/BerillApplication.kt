package dev.akkraizen.berill

import android.app.Application
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.createAndroidCore

class BerillApplication : Application() {
  val core: BerillCore by lazy { createAndroidCore(this, BerillPlugins) }
}
