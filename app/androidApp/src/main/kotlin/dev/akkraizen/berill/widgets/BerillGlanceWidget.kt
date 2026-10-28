package dev.akkraizen.berill.widgets

import android.content.Context
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import dev.akkraizen.berill.core.ui.theme.cachedAndroidAppearance
import dev.akkraizen.berill.core.ui.theme.widgetColorSchemes

class BerillGlanceWidget : GlanceAppWidget() {
  override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    WidgetHost.prepare(context, id)
    provideContent {
      val schemes = remember(currentState<Preferences>()) { widgetColorSchemes(cachedAndroidAppearance(context)) }
      GlanceTheme(colors = schemes?.let { ColorProviders(light = it.first, dark = it.second) } ?: GlanceTheme.colors) {
        WidgetSurface(currentState<Preferences>())
      }
    }
  }
}
