package dev.marzahn.gloriana.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

class ContrastTest {
  private fun contrast(a: Color, b: Color): Float {
    val lighter = max(a.luminance(), b.luminance())
    val darker = min(a.luminance(), b.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
  }

  private fun assertContrast(name: String, foreground: Color, background: Color, minimum: Float) {
    val ratio = contrast(foreground, background)
    assertTrue(ratio >= minimum, "$name is $ratio:1, needs $minimum:1")
  }

  private fun LookColors.textGrounds() = mapOf(
    "canvas" to canvas, "rail" to rail, "pane" to pane, "raised" to raised,
    "surface-2" to surface2, "surface-3" to surface3, "accent-soft" to accentSoft,
  )

  private fun LookColors.texts() = mapOf(
    "text" to text, "text-muted" to textMuted, "text-subtle" to textSubtle,
    "accent-text" to accentText, "danger-text" to dangerText,
  )

  private fun checkMode(mode: String, colors: LookColors) {
    colors.textGrounds().forEach { (ground, background) ->
      colors.texts().forEach { (name, foreground) ->
        assertContrast("$mode $name on $ground", foreground, background, TEXT)
      }
      assertContrast("$mode accent on $ground", colors.accent, background, GRAPHIC)
    }
    assertContrast("$mode danger-text on danger-soft", colors.dangerText, colors.dangerSoft, TEXT)
    assertContrast("$mode accent-fg on accent", colors.accentFg, colors.accent, TEXT)
    assertContrast("$mode accent-fg on accent-hover", colors.accentFg, colors.accentHover, TEXT)
    assertContrast("$mode on-danger on danger", colors.onDanger, colors.danger, TEXT)
    assertContrast("$mode on-danger on danger-text", colors.onDanger, colors.dangerText, TEXT)
    assertContrast("$mode inverse-fg on inverse", colors.inverseFg, colors.inverse, TEXT)
    assertContrast("$mode inverse accent on inverse", colors.inverseAccent, colors.inverse, TEXT)
  }

  @Test
  fun lightPairsReachTheirContrast() = checkMode("light", LightLookColors)

  @Test
  fun darkPairsReachTheirContrast() = checkMode("dark", DarkLookColors)

  @Test
  fun avatarLettersReadOnEveryGradientStopInBothModes() {
    val tokens = defaultLookTokens(LookFonts(FontFamily.Serif, FontFamily.SansSerif))
    listOf(tokens.light, tokens.dark).forEach { set ->
      tokens.avatarGradients.forEachIndexed { index, gradient ->
        assertContrast("letter on gradient $index start", set.colors.onAvatar, gradient.start, TEXT)
        assertContrast("letter on gradient $index end", set.colors.onAvatar, gradient.end, TEXT)
      }
    }
  }

  private companion object {
    const val TEXT = 4.5f
    const val GRAPHIC = 3f
  }
}
