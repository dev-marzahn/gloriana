package dev.marzahn.gloriana.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.inter_medium
import dev.marzahn.gloriana.ui.resources.inter_regular
import dev.marzahn.gloriana.ui.resources.literata_bold
import dev.marzahn.gloriana.ui.resources.literata_bold_italic
import dev.marzahn.gloriana.ui.resources.literata_italic
import dev.marzahn.gloriana.ui.resources.literata_regular
import org.jetbrains.compose.resources.Font

private val WarmShadow = Color(0xFF3C322D)
private val BaseRadius = 16.dp

private val DefaultSpacing = Spacing(
  s6 = 6.dp,
  s8 = 8.dp,
  s10 = 10.dp,
  s12 = 12.dp,
  s14 = 14.dp,
  s16 = 16.dp,
  s20 = 20.dp,
  s24 = 24.dp,
)

private val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val FadeThroughOut = MotionToken(durationMillis = 90, easing = EmphasizedAccelerate)
private val PaneOut = MotionToken(durationMillis = 150, easing = EmphasizedAccelerate)

private val DefaultMotion = LookMotion(
  standard = MotionToken(durationMillis = 150, easing = StandardEasing),
  enter = MotionToken(durationMillis = 300, easing = EmphasizedDecelerate),
  exit = MotionToken(durationMillis = 200, easing = EmphasizedAccelerate),
  fadeThroughOut = FadeThroughOut,
  fadeThroughIn = MotionToken(durationMillis = 210, easing = EmphasizedDecelerate).after(FadeThroughOut),
  paneOut = PaneOut,
  paneIn = MotionToken(durationMillis = 150, easing = EmphasizedDecelerate).after(PaneOut),
)

@Composable
fun bundledLookFonts(): LookFonts = LookFonts(
  display = FontFamily(
    Font(Res.font.literata_regular, FontWeight.Normal, FontStyle.Normal),
    Font(Res.font.literata_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.literata_bold, FontWeight.Bold, FontStyle.Normal),
    Font(Res.font.literata_bold_italic, FontWeight.Bold, FontStyle.Italic),
  ),
  ui = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
  ),
)

@Composable
fun rememberDefaultLookTokens(): LookTokens {
  val fonts = bundledLookFonts()
  return remember(fonts) { defaultLookTokens(fonts) }
}

fun defaultLookTokens(fonts: LookFonts): LookTokens = LookTokens(
  light = defaultLookSet(
    LightLookColors,
    LookShadows(
      soft = LookShadow(1.dp, WarmShadow),
      elevated = LookShadow(12.dp, WarmShadow.copy(alpha = 0.12f)),
      warm = LookShadow(6.dp, LightLookColors.accent.copy(alpha = 0.7f)),
    ),
  ),
  dark = defaultLookSet(
    DarkLookColors,
    LookShadows(
      soft = LookShadow(1.dp, Color.Black),
      elevated = LookShadow(12.dp, Color.Black.copy(alpha = 0.55f)),
      warm = LookShadow(6.dp, DarkLookColors.accent.copy(alpha = 0.35f)),
    ),
  ),
  fonts = fonts,
  baseRadius = BaseRadius,
  spacing = DefaultSpacing,
  motion = DefaultMotion,
)

private fun defaultLookSet(colors: LookColors, shadows: LookShadows): LookSet {
  val bubbleShape = RoundedCornerShape(BaseRadius)
  val bubblePadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
  return LookSet(
    colors = colors,
    shadows = shadows,
    chat = ChatLook(
      background = ChatBackground.Plain(colors.canvas),
      containerAlpha = 1f,
      blurRadius = 0.dp,
      characterBubble = Bubble(colors.raised, colors.text, colors.border, bubbleShape, bubblePadding),
      userBubble = Bubble(colors.accentSoft, colors.text, colors.accentRing, bubbleShape, bubblePadding),
      highlights = emptyMap(),
    ),
  )
}
