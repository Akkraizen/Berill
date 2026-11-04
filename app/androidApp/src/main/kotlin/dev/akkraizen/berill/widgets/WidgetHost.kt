package dev.akkraizen.berill.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.akkraizen.berill.BerillApplication
import dev.akkraizen.berill.MainActivity
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.ui.theme.cachedAndroidAppearance
import dev.akkraizen.berill.core.widgets.WidgetHub
import dev.akkraizen.berill.core.widgets.WidgetInstance
import dev.akkraizen.berill.core.widgets.WidgetText
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal object WidgetHost {
  val KEY = stringPreferencesKey("berill.widget.key")
  val CHOICE = stringPreferencesKey("berill.widget.choice")
  val STATE = stringPreferencesKey("berill.widget.state")
  val CONTENT = stringPreferencesKey("berill.widget.content")

  const val EXTRA_WIDGET_ID = "dev.akkraizen.berill.widget.ID"
  const val EXTRA_WIDGET_ACTION = "dev.akkraizen.berill.widget.ACTION"

  private val json = Json { ignoreUnknownKeys = true }
  private val stateSerializer = MapSerializer(String.serializer(), String.serializer())
  private val refreshLock = Mutex()

  fun core(context: Context): BerillCore = (context.applicationContext as BerillApplication).core

  private fun prepareLanguage(context: Context) {
    runCatching { I18n.language = cachedAndroidAppearance(context).appLanguage }
  }

  fun encodeState(state: Map<String, String>): String = json.encodeToString(stateSerializer, state)

  fun decodeState(text: String?): Map<String, String> =
    text?.let { runCatching { json.decodeFromString(stateSerializer, it) }.getOrNull() }.orEmpty()

  fun specOf(context: Context, appWidgetId: Int): WidgetSpec? =
    AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className?.let(WidgetCatalog::spec)

  fun appWidgetId(context: Context, glanceId: GlanceId): Int = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)

  suspend fun instanceOf(context: Context, glanceId: GlanceId): WidgetInstance? {
    val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
    return instanceOf(prefs, specOf(context, appWidgetId(context, glanceId))?.key)
  }

  fun instanceOf(prefs: Preferences, fixedKey: String?): WidgetInstance? {
    val key = fixedKey ?: prefs[KEY] ?: return null
    return WidgetInstance(key, prefs[CHOICE], decodeState(prefs[STATE]))
  }

  private val prepared = ConcurrentHashMap<String, Long>()
  private val pendingAll = AtomicBoolean(false)

  /** Whether [provideGlance] can reuse content that [refresh] stored a moment ago. */
  fun recentlyPrepared(glanceId: GlanceId): Boolean =
    prepared[glanceId.toString()]?.let { System.currentTimeMillis() - it < FRESH_MS } == true

  /** Rebuilds the content of one widget; returns false when it did not change. */
  suspend fun prepare(context: Context, glanceId: GlanceId): Boolean =
    withContext(Dispatchers.IO) {
      prepareLanguage(context)
      val hub = core(context).widgets
      val instance = instanceOf(context, glanceId)
      val content = if (instance == null) WidgetHub.notice(WidgetText.pickerTitle(), WidgetText.chooseHint()) else hub.content(instance)
      val encoded = hub.encode(content)
      var changed = false
      updateAppWidgetState(context, glanceId) { prefs ->
        if (prefs[CONTENT] != encoded) {
          prefs[CONTENT] = encoded
          changed = true
        }
        if (instance != null && prefs[KEY] != instance.key) prefs[KEY] = instance.key
      }
      prepared[glanceId.toString()] = System.currentTimeMillis()
      changed
    }

  suspend fun refresh(context: Context, glanceId: GlanceId, force: Boolean = true) {
    runCatching {
      if (prepare(context, glanceId) || force) BerillGlanceWidget().update(context, glanceId)
    }.onFailure { AppLog.error("widgets", "refresh failed", it) }
  }

  private suspend fun installed(context: Context): List<Pair<GlanceId, String?>> {
    val manager = GlanceAppWidgetManager(context)
    val appWidgets = AppWidgetManager.getInstance(context)
    val result = mutableListOf<Pair<GlanceId, String?>>()
    for (spec in WidgetCatalog.specs)
      for (appWidgetId in appWidgets.getAppWidgetIds(ComponentName(context, spec.receiver))) {
        val glanceId = runCatching { manager.getGlanceIdBy(appWidgetId) }.getOrNull() ?: continue
        result += glanceId to spec.key
      }
    return result
  }

  suspend fun refreshAll(context: Context) = refreshChanged(context, null, force = true)

  suspend fun refreshOne(context: Context, appWidgetId: Int) {
    val glanceId = runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }.getOrNull()
    if (glanceId == null) refreshAll(context) else refreshLock.withLock { refresh(context, glanceId) }
  }

  /** Refreshes the widgets that read [collections] (all when null), redrawing only those whose content changed. */
  suspend fun refreshChanged(context: Context, collections: Set<String>?, force: Boolean = false) {
    if (collections == null && !pendingAll.compareAndSet(false, true)) return
    try {
      refreshLock.withLock {
        pendingAll.set(false)
        val hub = core(context).widgets
        for ((glanceId, fixedKey) in installed(context)) {
          val key = fixedKey ?: runCatching {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[KEY]
          }.getOrNull()
          if (key == null || hub.affects(key, collections)) refresh(context, glanceId, force)
        }
      }
    } finally {
      if (collections == null) pendingAll.set(false)
    }
  }

  fun scheduleMidnight(context: Context) {
    val now = java.time.ZonedDateTime.now()
    val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(1)
    val request = OneTimeWorkRequestBuilder<WidgetMidnightWorker>()
      .setInitialDelay(java.time.Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(MIDNIGHT_WORK, ExistingWorkPolicy.REPLACE, request)
  }

  suspend fun configure(context: Context, appWidgetId: Int, key: String, choice: String?) {
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    updateAppWidgetState(context, glanceId) { prefs ->
      prefs[KEY] = key
      if (choice == null) prefs.remove(CHOICE) else prefs[CHOICE] = choice
      prefs.remove(STATE)
    }
    refresh(context, glanceId)
  }

  suspend fun perform(context: Context, glanceId: GlanceId, actionJson: String): WidgetEffect? =
    withContext(Dispatchers.IO) {
      prepareLanguage(context)
      val hub = core(context).widgets
      val action = hub.decodeAction(actionJson) ?: return@withContext null
      val instance = instanceOf(context, glanceId) ?: return@withContext null
      val effect = hub.perform(instance, action)
      apply(context, glanceId, effect)
      effect
    }

  suspend fun perform(context: Context, appWidgetId: Int, actionJson: String) {
    val glanceId = runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }.getOrNull()
    if (glanceId != null) {
      perform(context, glanceId, actionJson)
      return
    }
    withContext(Dispatchers.IO) {
      val hub = core(context).widgets
      val action = hub.decodeAction(actionJson) ?: return@withContext
      val key = specOf(context, appWidgetId)?.key ?: return@withContext
      hub.perform(WidgetInstance(key), action)
    }
  }

  private suspend fun apply(context: Context, glanceId: GlanceId, effect: WidgetEffect) {
    val state = when (effect) {
      is WidgetEffect.Refresh -> effect.state
      is WidgetEffect.OpenApp -> effect.state
    }
    if (state != null)
      updateAppWidgetState(context, glanceId) { prefs ->
        if (state.isEmpty()) prefs.remove(STATE) else prefs[STATE] = encodeState(state)
      }
    if (effect is WidgetEffect.Refresh && effect.refreshAtMillis != null)
      scheduleRefresh(context, appWidgetId(context, glanceId), effect.refreshAtMillis!!)
    refresh(context, glanceId)
  }

  fun scheduleRefresh(context: Context, appWidgetId: Int, atMillis: Long) {
    val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
    val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
      .setInitialDelay(delay, TimeUnit.MILLISECONDS)
      .setInputData(androidx.work.workDataOf(WidgetRefreshWorker.APP_WIDGET_ID to appWidgetId))
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork("berill-widget-$appWidgetId", ExistingWorkPolicy.REPLACE, request)
  }

  fun openIntent(context: Context, appWidgetId: Int, action: WidgetAction, actionJson: String): Intent =
    Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      data = "berill-widget://$appWidgetId/${actionJson.hashCode().toUInt()}/${action::class.simpleName}".toUri()
      putExtra(EXTRA_WIDGET_ID, appWidgetId)
      putExtra(EXTRA_WIDGET_ACTION, actionJson)
    }

  fun handleIntent(context: Context, intent: Intent?, scope: CoroutineScope) {
    val actionJson = intent?.getStringExtra(EXTRA_WIDGET_ACTION) ?: return
    val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    intent.removeExtra(EXTRA_WIDGET_ACTION)
    val app = context.applicationContext
    scope.launch(Dispatchers.IO) {
      runCatching { perform(app, appWidgetId, actionJson) }
        .onFailure { AppLog.error("widgets", "open action failed", it) }
    }
  }

  fun observe(context: Context, core: BerillCore, scope: CoroutineScope) {
    val app = context.applicationContext
    scheduleMidnight(app)
    scope.launch {
      core.widgets.invalidations.collect { collections -> refreshChanged(app, collections) }
    }
  }

  private const val FRESH_MS = 3_000L
  private const val MIDNIGHT_WORK = "berill-widgets-midnight"
}
