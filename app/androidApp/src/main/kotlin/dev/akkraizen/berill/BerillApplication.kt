package dev.akkraizen.berill

import android.app.Application
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.createAndroidCore
import dev.akkraizen.berill.widgets.WidgetHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BerillApplication : Application() {
  val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val core: BerillCore by lazy {
    createAndroidCore(this, BerillPlugins).also { WidgetHost.observe(this, it, scope) }
  }
}
