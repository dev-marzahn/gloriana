package dev.marzahn.gloriana.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
class Look(val tokens: LookTokens, val set: LookSet, val widthClass: WidthClass) {
  val shapes = LookShapes(tokens.baseRadius)
  val type = lookType(tokens.fonts, widthClass)
}

internal val LocalLook = staticCompositionLocalOf<Look> { error("No GlorianaTheme above this composable") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlorianaTheme(
  widthClass: WidthClass,
  tokens: LookTokens = rememberDefaultLookTokens(),
  dark: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val set = tokens.forMode(dark)
  val look = remember(tokens, set, widthClass) { Look(tokens, set, widthClass) }
  MaterialTheme(
    colorScheme = remember(set.colors) { set.colors.toColorScheme() },
    typography = look.type.material,
    shapes = look.shapes.material,
  ) {
    CompositionLocalProvider(
      LocalLook provides look,
      LocalRippleConfiguration provides null,
      LocalIndication provides GlorianaIndication(look.shapes.control),
      LocalMinimumInteractiveComponentSize provides widthClass.minimumTouchTarget,
      content = content,
    )
  }
}

object GlorianaTheme {
  val colors: LookColors
    @Composable @ReadOnlyComposable get() = LocalLook.current.set.colors

  val fonts: LookFonts
    @Composable @ReadOnlyComposable get() = LocalLook.current.tokens.fonts

  val shapes: LookShapes
    @Composable @ReadOnlyComposable get() = LocalLook.current.shapes

  val shadows: LookShadows
    @Composable @ReadOnlyComposable get() = LocalLook.current.set.shadows

  val type: LookType
    @Composable @ReadOnlyComposable get() = LocalLook.current.type

  val spacing: Spacing
    @Composable @ReadOnlyComposable get() = LocalLook.current.tokens.spacing

  val motion: LookMotion
    @Composable @ReadOnlyComposable get() = LocalLook.current.tokens.motion

  val chat: ChatLook
    @Composable @ReadOnlyComposable get() = LocalLook.current.set.chat

  val widthClass: WidthClass
    @Composable @ReadOnlyComposable get() = LocalLook.current.widthClass
}
