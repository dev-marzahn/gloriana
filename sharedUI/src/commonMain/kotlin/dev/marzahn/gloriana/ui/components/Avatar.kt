package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.marzahn.gloriana.logic.brand.AvatarGradientColors
import dev.marzahn.gloriana.logic.brand.avatarGradientColors
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_person
import dev.marzahn.gloriana.ui.resources.ic_person_20
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.painterResource
import kotlin.uuid.Uuid

private const val LETTER_SHARE = 0.4f
private const val OPAQUE = 0xFF shl 24
private val SmallIconMax = 20.dp

private val combiningMarks = setOf(
  CharCategory.NON_SPACING_MARK,
  CharCategory.COMBINING_SPACING_MARK,
  CharCategory.ENCLOSING_MARK,
)

@Immutable
data class AvatarGradient(val start: Color, val end: Color)

fun avatarGradient(id: Uuid): AvatarGradient = avatarGradientColors(id).toAvatarGradient()

internal fun AvatarGradientColors.toAvatarGradient() = AvatarGradient(opaque(startRgb), opaque(endRgb))

private fun opaque(rgb: Int) = Color(OPAQUE or rgb)

fun avatarInitial(name: String, locale: Locale): String? {
  val trimmed = name.trimStart()
  val first = trimmed.firstOrNull()?.takeIf { it.isLetterOrDigit() } ?: return null
  val marks = trimmed.drop(1).takeWhile { it.category in combiningMarks }
  return (first + marks).toUpperCase(locale)
}

internal fun AvatarGradient.brushFromTopStart(direction: LayoutDirection): Brush {
  val topStart = if (direction == LayoutDirection.Ltr) Offset.Zero else Offset(Float.POSITIVE_INFINITY, 0f)
  val bottomEnd = if (direction == LayoutDirection.Ltr) Offset.Infinite else Offset(0f, Float.POSITIVE_INFINITY)
  return Brush.linearGradient(listOf(start, end), topStart, bottomEnd)
}

@Composable
fun Avatar(id: Uuid, name: String, size: AvatarSize, modifier: Modifier = Modifier) {
  val shape = if (size.shape == AvatarShape.Tile) GlorianaTheme.shapes.panel else GlorianaTheme.shapes.pill
  val brush = avatarGradient(id).brushFromTopStart(LocalLayoutDirection.current)
  val initial = avatarInitial(name, Locale.current)
  Box(modifier.size(size.side).clip(shape).background(brush), contentAlignment = Alignment.Center) {
    if (initial == null) NoNameIcon(size.side / 2) else AvatarLetter(initial, size.side)
  }
}

@Composable
private fun NoNameIcon(side: Dp) {
  val icon = if (side <= SmallIconMax) Res.drawable.ic_person_20 else Res.drawable.ic_person
  Icon(painterResource(icon), null, Modifier.size(side), tint = GlorianaTheme.colors.onAvatar)
}

@Composable
private fun AvatarLetter(initial: String, side: Dp) {
  val letterSize = (side.value * LETTER_SHARE).sp
  val style = TextStyle(fontFamily = GlorianaTheme.fonts.display, fontSize = letterSize, lineHeight = letterSize)
  val withoutFontScale = Density(LocalDensity.current.density, fontScale = 1f)
  CompositionLocalProvider(LocalDensity provides withoutFontScale) {
    Text(initial, style = style, color = GlorianaTheme.colors.onAvatar)
  }
}
