package dev.akkraizen.berill.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.BerillApplication
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.widgets.WidgetChoice
import dev.akkraizen.berill.core.ui.theme.BerillTheme
import dev.akkraizen.berill.core.ui.theme.cachedAndroidAppearance
import dev.akkraizen.berill.core.widgets.WidgetHub
import dev.akkraizen.berill.core.widgets.WidgetText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class PickerEntry(val key: String, val title: String, val description: String, val owner: String)

class WidgetConfigActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
      ?: AppWidgetManager.INVALID_APPWIDGET_ID
    setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish()
      return
    }
    val appearance = cachedAndroidAppearance(this)
    I18n.language = appearance.appLanguage
    val fixedKey = WidgetHost.specOf(this, appWidgetId)?.key
    setContent {
      BerillTheme(appearance) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          ConfigScreen(fixedKey, onDone = { key, choice -> finishWith(appWidgetId, key, choice) }, onCancel = { finish() })
        }
      }
    }
  }

  private fun finishWith(appWidgetId: Int, key: String, choice: String?) {
    val app = applicationContext
    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
    (application as BerillApplication).scope.launch {
      WidgetHost.configure(app, appWidgetId, key, choice)
    }
    finish()
  }
}

@Composable
private fun ConfigScreen(fixedKey: String?, onDone: (String, String?) -> Unit, onCancel: () -> Unit) {
  val context = LocalContext.current
  var entries by remember { mutableStateOf<List<PickerEntry>?>(null) }
  var selected by remember { mutableStateOf(fixedKey) }
  var choices by remember { mutableStateOf<List<WidgetChoice>?>(null) }
  var optional by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    entries = withContext(Dispatchers.IO) {
      WidgetHost.core(context).widgets.available().map { PickerEntry(it.key, it.title, it.description, it.ownerTitle()) }
    }
  }
  LaunchedEffect(selected) {
    val key = selected ?: return@LaunchedEffect
    choices = null
    val hub: WidgetHub = withContext(Dispatchers.IO) { WidgetHost.core(context).widgets }
    val provider = hub.entry(key)?.provider
    if (provider?.choices == null) {
      onDone(key, null)
      return@LaunchedEffect
    }
    optional = provider.choices?.required == false
    choices = withContext(Dispatchers.IO) { hub.choices(key) }
  }
  BackHandler {
    if (selected != null && fixedKey == null) selected = null else onCancel()
  }

  Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        val title = selected?.let { key -> entries?.firstOrNull { it.key == key }?.title } ?: WidgetText.pickerTitle()
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
          if (selected == null) WidgetText.pickerHint() else WidgetText.pickerChoose(),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      if (selected != null && fixedKey == null) TextButton(onClick = { selected = null }) { Text(WidgetText.pickerBack()) }
    }
    val key = selected
    when {
      key == null -> {
        val list = entries
        if (list == null) Loading()
        else if (list.isEmpty()) Hint(WidgetText.pickerEmpty())
        else
          OptionList(list.map { Option(it.key, it.title, listOf(it.owner, it.description).filter { s -> s.isNotBlank() }.joinToString(" · ")) }) {
            selected = it
          }
      }
      choices == null -> Loading()
      else -> {
        val options = (if (optional) listOf(Option(ANY, WidgetText.pickerAny(), null)) else emptyList()) +
          choices.orEmpty().map { Option(it.id, it.label, it.hint) }
        if (options.isEmpty()) Hint(WidgetText.pickerNoChoices())
        else OptionList(options) { id -> onDone(key, id.takeIf { it != ANY }) }
      }
    }
  }
}

private const val ANY = "\u0000any"

private class Option(val id: String, val title: String, val hint: String?)

@Composable
private fun OptionList(options: List<Option>, onPick: (String) -> Unit) {
  LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
    items(options, key = { it.id }) { option ->
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable { onPick(option.id) }
      ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
          Text(option.title, style = MaterialTheme.typography.titleMedium)
          option.hint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
private fun Loading() {
  Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Hint(text: String) {
  Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
}
