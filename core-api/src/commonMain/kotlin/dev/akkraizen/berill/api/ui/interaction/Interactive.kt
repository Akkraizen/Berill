package dev.akkraizen.berill.api.ui.interaction

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.feedback.color

private const val HOVER_ALPHA = 0.08f

private const val FOCUS_ALPHA = 0.10f

private const val PRESS_ALPHA = 0.16f

@Composable
fun Modifier.interactive(
  shape: Shape = MaterialTheme.shapes.small,
  bleed: Dp = 0.dp,
  enabled: Boolean = true,
  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit
): Modifier {
  val interaction = remember { MutableInteractionSource() }
  val hovered by interaction.collectIsHoveredAsState()
  val pressed by interaction.collectIsPressedAsState()
  val focused by interaction.collectIsFocusedAsState()
  val target = when {
    !enabled -> 0f
    pressed -> PRESS_ALPHA
    focused -> FOCUS_ALPHA
    hovered -> HOVER_ALPHA
    else -> 0f
  }
  val alpha = animateFloatAsState(target, BerillMotion.effects(), label = "state-layer")
  val color = LocalContentColor.current
  return this.drawWithContent {
    drawContent()
    val a = alpha.value
    if (a > 0.001f) {
      val b = bleed.toPx()
      val outline =
        shape.createOutline(Size(size.width + 2 * b, size.height + 2 * b), layoutDirection, this)
      translate(-b, -b) { drawOutline(outline, color.copy(alpha = a)) }
    }
  }
    .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
    .hoverable(interaction, enabled)
    .combinedClickable(
      interactionSource = interaction,
      indication = null,
      enabled = enabled,
      onLongClick = onLongClick,
      onClick = onClick
    )
}

@Composable
fun BerillTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  textStyle: TextStyle = LocalTextStyle.current,
  label: (@Composable () -> Unit)? = null,
  placeholder: (@Composable () -> Unit)? = null,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  supportingText: (@Composable () -> Unit)? = null,
  isError: Boolean = false,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  singleLine: Boolean = false,
  maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
  minLines: Int = 1,
  shape: Shape = OutlinedTextFieldDefaults.shape,
  onSubmit: (() -> Unit)? = null
) {
  val submit: (() -> Unit)? = when {
    !singleLine -> null
    onSubmit != null -> onSubmit
    keyboardActions == KeyboardActions.Default -> {
      {
        HotKeys.runPrimary()
        Unit
      }
    }

    else -> null
  }
  val interaction = remember { MutableInteractionSource() }
  val hovered by interaction.collectIsHoveredAsState()
  val focused by interaction.collectIsFocusedAsState()
  val onSurface = MaterialTheme.colorScheme.onSurface
  val fill by
  animateColorAsState(
    when {
      !enabled -> Color.Transparent
      hovered -> onSurface.copy(alpha = HOVER_ALPHA)
      focused -> onSurface.copy(alpha = 0.05f)
      else -> onSurface.copy(alpha = 0f)
    },
    BerillMotion.effects(),
    label = "field-fill"
  )
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier
      .hoverable(interaction, enabled)
      .trackTextInput()
      .then(
        if (submit == null)
          Modifier
        else
          Modifier.onPreviewKeyEvent { e ->
            if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
              if (e.type == KeyEventType.KeyDown) submit()
              true
            } else {
              false
            }
          }
      ),
    enabled = enabled,
    readOnly = readOnly,
    textStyle = textStyle,
    label = label,
    placeholder = placeholder,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    supportingText = supportingText,
    isError = isError,
    visualTransformation = visualTransformation,
    keyboardOptions = keyboardOptions,
    keyboardActions = if (submit != null) KeyboardActions(onAny = { submit() }) else keyboardActions,
    singleLine = singleLine,
    maxLines = maxLines,
    minLines = minLines,
    interactionSource = interaction,
    shape = shape,
    colors = OutlinedTextFieldDefaults.colors(
      focusedContainerColor = fill,
      unfocusedContainerColor = fill,
      errorContainerColor = fill
    )
  )
}

@Composable
fun BerillChip(
  onClick: (() -> Unit)?,
  label: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  border: BorderStroke? = null
) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(6.dp)
  val background by
  animateColorAsState(
    if (selected) colors.secondaryContainer else colors.secondaryContainer.copy(alpha = 0f),
    BerillMotion.effects(),
    label = "chip-bg"
  )
  val content by
  animateColorAsState(
    if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
    BerillMotion.effects(),
    label = "chip-fg"
  )
  val iconTint by
  animateColorAsState(
    if (selected) colors.onSecondaryContainer else colors.primary,
    BerillMotion.effects(),
    label = "chip-icon"
  )
  val clickable = if (onClick != null) Modifier.interactive(shape, onClick = onClick) else Modifier
  Surface(
    shape = shape,
    color = background,
    contentColor = content,
    border = border ?: if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
    modifier = modifier.then(clickable)
  ) {
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelLarge) {
      Row(
        Modifier.height(32.dp)
          .padding(
            start = if (leadingIcon != null) 8.dp else 16.dp,
            end = if (trailingIcon != null) 8.dp else 16.dp
          ),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (leadingIcon != null) {
          CompositionLocalProvider(LocalContentColor provides iconTint) { leadingIcon() }
          Spacer(Modifier.width(8.dp))
        }
        label()
        if (trailingIcon != null) {
          Spacer(Modifier.width(8.dp))
          trailingIcon()
        }
      }
    }
  }
}
