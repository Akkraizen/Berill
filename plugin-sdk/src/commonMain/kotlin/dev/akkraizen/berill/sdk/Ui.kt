package dev.akkraizen.berill.sdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TextStyle {
  Body,
  Title,
  Headline,
  Label,
  Caption
}

@Serializable
enum class Tone {
  Normal,
  Muted,
  Good,
  Bad,
  Warn,
  Info
}

@Serializable
enum class ButtonStyle {
  Filled,
  Tonal,
  Outlined,
  Text
}

@Serializable
enum class Keyboard {
  Text,
  Number,
  Decimal
}

@Serializable
data class Stat(
  val label: String,
  val value: String,
  val sub: String? = null,
  val tone: Tone = Tone.Normal,
  val icon: String? = null
)

@Serializable
data class ChipOption(val id: String, val label: String)

@Serializable
sealed interface UiNode {
  @Serializable
  @SerialName("column")
  data class Column(val children: List<UiNode>, val spacing: Int = 10) : UiNode

  @Serializable
  @SerialName("row")
  data class Row(val children: List<UiNode>, val spacing: Int = 8) : UiNode

  @Serializable
  @SerialName("weight")
  data class Weighted(val weight: Float, val child: UiNode) : UiNode

  @Serializable
  @SerialName("text")
  data class Text(
    val text: String,
    val style: TextStyle = TextStyle.Body,
    val tone: Tone = Tone.Normal
  ) : UiNode

  @Serializable
  @SerialName("header")
  data class Header(val title: String, val icon: String? = null) : UiNode

  @Serializable
  @SerialName("card")
  data class Card(
    val title: String? = null,
    val icon: String? = null,
    val children: List<UiNode>,
    val action: String? = null
  ) : UiNode

  @Serializable
  @SerialName("button")
  data class Button(
    val label: String,
    val action: String,
    val style: ButtonStyle = ButtonStyle.Filled,
    val icon: String? = null,
    val enabled: Boolean = true
  ) : UiNode

  @Serializable
  @SerialName("icon_button")
  data class IconButton(val icon: String, val description: String, val action: String) : UiNode

  @Serializable
  @SerialName("field")
  data class TextField(
    val key: String,
    val value: String,
    val label: String,
    val action: String,
    val singleLine: Boolean = true,
    val keyboard: Keyboard = Keyboard.Text,
    val placeholder: String? = null,
    val submitAction: String? = null
  ) : UiNode

  @Serializable
  @SerialName("checkbox")
  data class Checkbox(val checked: Boolean, val label: String, val action: String) : UiNode

  @Serializable
  @SerialName("switch")
  data class Switch(val checked: Boolean, val label: String, val action: String) : UiNode

  @Serializable
  @SerialName("chips")
  data class Chips(val options: List<ChipOption>, val selected: String?, val action: String) :
    UiNode

  @Serializable
  @SerialName("stats")
  data class Stats(val items: List<Stat>) : UiNode

  @Serializable
  @SerialName("progress")
  data class Progress(val fraction: Float) : UiNode

  @Serializable
  @SerialName("list_item")
  data class ListItem(
    val title: String,
    val subtitle: String? = null,
    val trailing: String? = null,
    val icon: String? = null,
    val action: String? = null
  ) : UiNode

  @Serializable
  @SerialName("divider")
  data object Divider : UiNode

  @Serializable
  @SerialName("spacer")
  data class Spacer(val height: Int = 8) : UiNode

  @Serializable
  @SerialName("empty")
  data class Empty(val text: String) : UiNode

  @Serializable
  @SerialName("image")
  data class Image(val url: String, val height: Int = 160) : UiNode

  @Serializable
  @SerialName("markdown")
  data class Markdown(val text: String) : UiNode
}

@DslMarker
annotation class UiDsl

@RequiresOptIn(
  level = RequiresOptIn.Level.ERROR,
  message = "Berill's internal API: the plugin doesn't need it"
)
annotation class InternalSdkApi

@UiDsl
class UiBuilder
@InternalSdkApi
constructor(private val actions: MutableMap<String, (String?) -> Unit>) {
  private val nodes = mutableListOf<UiNode>()

  @InternalSdkApi
  fun build(): List<UiNode> = nodes.toList()

  private fun action(handler: (String?) -> Unit): String {
    val id = "a${actions.size + 1}"
    actions[id] = handler
    return id
  }

  @OptIn(InternalSdkApi::class)
  private fun children(content: UiBuilder.() -> Unit): List<UiNode> =
    UiBuilder(actions).apply(content).nodes

  private fun add(node: UiNode) {
    nodes += node
  }

  fun text(
    text: String,
    style: TextStyle = TextStyle.Body,
    tone: Tone = Tone.Normal
  ) = add(UiNode.Text(text, style, tone))

  fun header(title: String, icon: String? = null) = add(UiNode.Header(title, icon))

  fun card(
    title: String? = null,
    icon: String? = null,
    onClick: (() -> Unit)? = null,
    content: UiBuilder.() -> Unit
  ) = add(UiNode.Card(title, icon, children(content), onClick?.let { h -> action { h() } }))

  fun column(spacing: Int = 10, content: UiBuilder.() -> Unit) =
    add(UiNode.Column(children(content), spacing))

  fun row(spacing: Int = 8, content: UiBuilder.() -> Unit) =
    add(UiNode.Row(children(content), spacing))

  fun weight(weight: Float = 1f, content: UiBuilder.() -> Unit) {
    val inner = children(content)
    add(UiNode.Weighted(weight, inner.singleOrNull() ?: UiNode.Column(inner)))
  }

  fun button(
    label: String,
    style: ButtonStyle = ButtonStyle.Filled,
    icon: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
  ) = add(UiNode.Button(label, action { onClick() }, style, icon, enabled))

  fun iconButton(
    icon: String,
    description: String,
    onClick: () -> Unit
  ) = add(UiNode.IconButton(icon, description, action { onClick() }))

  fun textField(
    value: String,
    label: String,
    key: String = label,
    singleLine: Boolean = true,
    keyboard: Keyboard = Keyboard.Text,
    placeholder: String? = null,
    onSubmit: (() -> Unit)? = null,
    onChange: (String) -> Unit
  ) = add(
    UiNode.TextField(
      key = key,
      value = value,
      label = label,
      action = action { onChange(it.orEmpty()) },
      singleLine = singleLine,
      keyboard = keyboard,
      placeholder = placeholder,
      submitAction = onSubmit?.let { h -> action { h() } }
    )
  )

  fun checkbox(
    checked: Boolean,
    label: String,
    onChange: (Boolean) -> Unit
  ) = add(UiNode.Checkbox(checked, label, action { onChange(it == "true") }))

  fun switch(
    checked: Boolean,
    label: String,
    onChange: (Boolean) -> Unit
  ) = add(UiNode.Switch(checked, label, action { onChange(it == "true") }))

  fun chips(options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) =
    add(
      UiNode.Chips(
        options.map { ChipOption(it.first, it.second) },
        selected,
        action { v -> v?.let(onSelect) })
    )

  fun stats(vararg items: Stat) = add(UiNode.Stats(items.toList()))

  fun stats(items: List<Stat>) = add(UiNode.Stats(items))

  fun progress(fraction: Float) = add(UiNode.Progress(fraction))

  fun listItem(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    icon: String? = null,
    onClick: (() -> Unit)? = null
  ) = add(UiNode.ListItem(title, subtitle, trailing, icon, onClick?.let { h -> action { h() } }))

  fun divider() = add(UiNode.Divider)

  fun spacer(height: Int = 8) = add(UiNode.Spacer(height))

  fun empty(text: String) = add(UiNode.Empty(text))

  fun image(url: String, height: Int = 160) = add(UiNode.Image(url, height))

  fun markdown(text: String) = add(UiNode.Markdown(text))
}
