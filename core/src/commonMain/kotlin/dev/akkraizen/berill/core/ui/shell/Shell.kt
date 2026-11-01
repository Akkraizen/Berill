package dev.akkraizen.berill.core.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import dev.akkraizen.berill.api.ui.interaction.LocalShortcuts
import dev.akkraizen.berill.core.connections.handleShortcut
import androidx.compose.runtime.remember
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.*
import dev.akkraizen.berill.core.connections.ConnectionTools
import dev.akkraizen.berill.core.connections.ConnectionDialogs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.PlatformBackHandler
import dev.akkraizen.berill.core.nav.Destination
import dev.akkraizen.berill.core.ui.CoreText
import dev.akkraizen.berill.core.ui.feedback.UndoDeletions
import dev.akkraizen.berill.core.ui.onboarding.Onboarding
import dev.akkraizen.berill.core.ui.theme.BerillTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BerillShell(
  core: BerillCore,
  windowChrome: (@Composable (title: String) -> Unit)? = null,
  overlay: @Composable () -> Unit = {}
) {
  val appearance by core.appearance.state.collectAsState()

  val shortcuts by core.shortcuts.state.collectAsState()
  CompositionLocalProvider(LocalShortcuts provides shortcuts) {
  key(I18n.language) {
    BerillTheme(appearance) {
      val stack by core.nav.stack.collectAsState()
      val plugins by core.plugins.enabled.collectAsState()
      val current = stack.last()
      val noteId = (current as? Destination.Note)?.id
      val editorContent = remember(core, noteId) { movableContentOf<Boolean> { showBack ->
        noteId?.let { dev.akkraizen.berill.core.ui.notes.NoteEditor(core, it, showBack) }
      } }
      val editorSlot = remember(noteId, editorContent) { dev.akkraizen.berill.core.ui.notes.NoteEditorSlot(noteId, editorContent) }
      val focusedNote by core.focusedNote.collectAsState()
      val focusMode = current is Destination.Note && focusedNote == current.id
      LaunchedEffect(current) { if (current !is Destination.Note || focusedNote != current.id) core.focusedNote.value = null }
      PlatformBackHandler(enabled = stack.size > 1 || focusMode) { if (focusMode) core.focusedNote.value = null else core.nav.back() }
      var expandedSections by rememberSaveable { mutableStateOf(emptyList<String>()) }
      val activeSection = (current as? Destination.Plugin)?.id ?: (current as? Destination.PluginSettings)?.id
      LaunchedEffect(activeSection) {
        if (activeSection != null && "plugin:$activeSection" !in expandedSections)
          expandedSections = expandedSections + "plugin:$activeSection"
      }
      val model = navModel(core, current, plugins, expandedSections.toSet()) { key ->
        expandedSections = if (key in expandedSections) expandedSections - key else expandedSections + key
      }
      val title = titleOf(current, core.plugins.all)

      val deep = (current is Destination.Plugin && (current.route != null || stack.size > 2)) ||
        current is Destination.PluginSettings || current is Destination.SettingsSection
      val onboarded by core.onboarded.collectAsState()

      CompositionLocalProvider(dev.akkraizen.berill.core.ui.notes.LocalNoteEditorSlot provides editorSlot) {
      Box(Modifier.fillMaxSize().onPreviewKeyEvent { e -> core.handleShortcut(e)
      }) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
          windowChrome?.invoke(if (onboarded) title else "Berill")

          AnimatedContent(
            targetState = onboarded,
            transitionSpec = {
              (
                fadeIn(BerillMotion.slowEffects()) +
                  scaleIn(BerillMotion.spatial(), initialScale = 0.94f)
              ) togetherWith
                (
                  fadeOut(BerillMotion.effects()) +
                    scaleOut(BerillMotion.spatial(), targetScale = 1.04f)
                )
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = "onboarding"
          ) { done ->
            if (!done)
              Onboarding(core, onFinish = core::finishOnboarding)
            else
              BoxWithConstraints(Modifier.fillMaxSize()) {
                if (focusMode && current is Destination.Note) {
                  Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                    Surface(Modifier.widthIn(max = 800.dp).fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                      dev.akkraizen.berill.core.ui.notes.NotesScreen(core, current.id, twoPane = false)
                    }
                  }
                } else if (maxWidth >= 840.dp) {
                  var expanded by rememberSaveable { mutableStateOf(true) }
                  Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                      AnimatedSidebar(model, expanded, onToggle = { expanded = !expanded })
                      Column(Modifier.weight(1f)) {
                        if (windowChrome == null) TopAppBar(
                          title = { CompactTitle(title) },
                          navigationIcon = {
                            if (deep) IconButton(onClick = { core.nav.back() }) {
                              Icon(Icons.AutoMirrored.Filled.ArrowBack, CoreText.back())
                            }
                          },
                          actions = { ConnectionTools(core) }
                        )
                        Content(
                          core,
                          stack,
                          plugins,
                          showHeader = windowChrome != null,
                          modifier = Modifier.weight(1f)
                        )
                      }
                    }
                  }
                } else {
                  val drawerState = rememberDrawerState(DrawerValue.Closed)
                  val scope = rememberCoroutineScope()

                  PlatformBackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                  }
                  ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                      ModalDrawerSheet(Modifier.width(300.dp)) {
                        DrawerContent(model, onCollapse = null, onPicked = { scope.launch { drawerState.close() } })
                      }
                    }
                  ) {
                    Scaffold(
                      topBar = {
                        TopAppBar(
                          title = { CompactTitle(title) },
                          actions = { if (windowChrome == null) ConnectionTools(core) },
                          navigationIcon = {
                            if (deep) {
                              IconButton(onClick = { core.nav.back() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, CoreText.back())
                              }
                            } else {
                              IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, CoreText.menu())
                              }
                            }
                          }
                        )
                      }
                    ) { padding ->
                      Content(
                        core,
                        stack,
                        plugins,
                        showHeader = false,
                        modifier = Modifier.padding(padding)
                      )
                    }
                  }
                }
              }
          }
        }
        UndoDeletions(core.store, Modifier.align(Alignment.BottomCenter))
      }
      ConnectionDialogs(core)
      overlay()
      }
    }
  }
  }
}

@Composable
private fun CompactTitle(title: String) {
  val section = title.substringBefore(" · ", "")
  val page = title.substringAfter(" · ")
  if (section.isEmpty()) {
    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
  } else {
    Column {
      Text(
        page,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.titleMedium
      )
      Text(
        section,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
