package dev.akkraizen.berill.desktop.ui.tray

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogModalityType
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.akkraizen.berill.core.ui.theme.Appearance
import dev.akkraizen.berill.core.ui.theme.BerillTheme
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TrayMenuWindow(
  anchor: IntOffset,
  appearance: Appearance,
  onDismiss: () -> Unit,
  onOpen: () -> Unit,
  onExit: () -> Unit
) {
  var measuredSize by remember { mutableStateOf<IntSize?>(null) }
  val menuSize = measuredSize ?: IntSize(1, 1)
  val position = remember(anchor, menuSize) {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val config = environment.screenDevices.map { it.defaultConfiguration }
      .firstOrNull { it.bounds.contains(anchor.x, anchor.y) }
      ?: environment.defaultScreenDevice.defaultConfiguration
    val bounds = config.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
    trayMenuPosition(
      anchor,
      menuSize,
      IntRect(
        bounds.x + insets.left, bounds.y + insets.top,
        bounds.x + bounds.width - insets.right, bounds.y + bounds.height - insets.bottom
      )
    )
  }
  val dialogState = rememberDialogState(
    width = menuSize.width.dp, height = menuSize.height.dp,
    position = WindowPosition(position.x.dp, position.y.dp)
  )
  LaunchedEffect(position, menuSize) {
    dialogState.size = DpSize(menuSize.width.dp, menuSize.height.dp)
    dialogState.position = WindowPosition(position.x.dp, position.y.dp)
  }
  DialogWindow(
    onCloseRequest = onDismiss,
    state = dialogState,
    visible = measuredSize != null,
    title = TrayText.title(),
    decoration = WindowDecoration.Undecorated(),
    transparent = true,
    resizable = false,
    alwaysOnTop = true,
    modalityType = DialogModalityType.Modeless,
    onPreviewKeyEvent = { event ->
      if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
        onDismiss()
        true
      } else false
    }
  ) {
    val currentDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(window) {
      val listener = object : WindowAdapter() {
        override fun windowLostFocus(event: WindowEvent) = currentDismiss()
      }
      window.addWindowFocusListener(listener)
      onDispose { window.removeWindowFocusListener(listener) }
    }
    LaunchedEffect(anchor, measuredSize) {
      if (measuredSize != null) {
        window.toFront()
        window.requestFocus()
      }
    }
    BerillTheme(appearance) {
      val contentSize = trayMenuSize()
      LaunchedEffect(contentSize) { measuredSize = contentSize }
      TrayMenuContent(onOpen, onExit)
    }
  }
}

@Composable
internal fun TrayMenuContent(onOpen: () -> Unit, onExit: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Box(Modifier.fillMaxSize().padding(8.dp)) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      shape = RoundedCornerShape(18.dp),
      color = colors.background,
      border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)),
      shadowElevation = 8.dp
    ) {
      Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TrayMenuAction(
          TrayText.open(),
          Icons.AutoMirrored.Outlined.OpenInNew,
          primary = true,
          onClick = onOpen
        )
        TrayMenuAction(
          TrayText.exit(),
          Icons.AutoMirrored.Outlined.ExitToApp,
          danger = true,
          onClick = onExit
        )
      }
    }
  }
}

@Composable
private fun TrayMenuAction(
  label: String,
  icon: ImageVector,
  primary: Boolean = false,
  danger: Boolean = false,
  onClick: () -> Unit
) {
  val interaction = remember { MutableInteractionSource() }
  val hovered by interaction.collectIsHoveredAsState()
  val focused by interaction.collectIsFocusedAsState()
  val colors = MaterialTheme.colorScheme
  val active = hovered || focused
  val background = when {
    danger && active -> colors.errorContainer
    primary -> colors.primaryContainer.copy(alpha = if (active) 1f else 0.65f)
    active -> colors.onSurface.copy(alpha = 0.08f)
    else -> Color.Transparent
  }
  val foreground = when {
    danger -> if (active) colors.onErrorContainer else colors.error
    primary -> colors.onPrimaryContainer
    else -> colors.onSurface
  }
  Row(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(background)
      .hoverable(interaction)
      .clickable(
        interactionSource = interaction,
        indication = null,
        role = Role.Button,
        onClick = onClick
      )
      .padding(horizontal = 10.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
    Text(
      label,
      style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
      color = foreground,
      maxLines = 1,
      softWrap = false
    )
  }
}

@Composable
internal fun trayMenuSize(): IntSize {
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)
  val labels = listOf(TrayText.open(), TrayText.exit()).map {
    measurer.measure(it, style = style, maxLines = 1).size
  }
  return with(density) {
    val actionWidth = labels.maxOf { it.width }.toDp() + 18.dp + 8.dp + 20.dp
    val actionHeight = maxOf(labels.maxOf { it.height }.toDp(), 18.dp) + 18.dp
    IntSize(
      ceil((actionWidth + 48.dp).value).toInt().coerceAtLeast(180),
      ceil((actionHeight * 2 + 44.dp).value).toInt()
    )
  }
}