package dev.marzahn.gloriana.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp

@Immutable
data class LookTokens(
  val light: LookSet,
  val dark: LookSet,
  val fonts: LookFonts,
  val baseRadius: Dp,
  val spacing: Spacing,
  val motion: LookMotion,
) {
  fun forMode(dark: Boolean): LookSet = if (dark) this.dark else light
}

@Immutable
data class LookSet(val colors: LookColors, val shadows: LookShadows, val chat: ChatLook)

@Immutable
data class LookFonts(val display: FontFamily, val ui: FontFamily)

@Immutable
sealed interface ChatBackground {
  data class Plain(val color: Color) : ChatBackground

  data class Picture(val image: ImageBitmap) : ChatBackground
}

@Immutable
data class Bubble(
  val container: Color,
  val content: Color,
  val border: Color,
  val shape: Shape,
  val padding: PaddingValues,
)

enum class Highlight { Tone, Quote }

@Immutable
data class ChatLook(
  val background: ChatBackground,
  val containerAlpha: Float,
  val blurRadius: Dp,
  val characterBubble: Bubble,
  val userBubble: Bubble,
  val highlights: Map<Highlight, Color>,
)
