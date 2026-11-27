package dev.marzahn.gloriana.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LookTypeTest {
  private fun Typography.slots(): Map<String, TextStyle> = mapOf(
    "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
    "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
    "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
    "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
    "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
  )

  private fun LookType.allStyles() = material.slots() + mapOf("message" to message, "reading" to reading)

  private fun bundledFonts(): LookFonts {
    lateinit var fonts: LookFonts
    runComposeUiTest { setContent { fonts = bundledLookFonts() } }
    return fonts
  }

  @Test
  fun everyStyleAsksForAFaceTheAppBundles() {
    val fonts = bundledFonts()
    WidthClass.entries.forEach { widthClass ->
      lookType(fonts, widthClass).allStyles().forEach { (name, style) ->
        val family = style.fontFamily
        assertTrue(family == fonts.display || family == fonts.ui, "$name uses a family that isn't bundled")
        val faces = (family as FontListFontFamily).fonts.map { it.weight to it.style }
        val asked = style.fontWeight to (style.fontStyle ?: FontStyle.Normal)
        assertTrue(asked in faces, "$name asks for $asked, which isn't bundled")
      }
    }
  }

  @Test
  fun headingsAreInTheDisplayFamilyAndTheInterfaceInTheUiFamily() {
    val fonts = bundledFonts()
    val type = lookType(fonts, WidthClass.Compact)
    val display = setOf(
      "displayLarge", "displayMedium", "displaySmall", "headlineLarge", "headlineMedium", "headlineSmall", "titleLarge",
    )
    type.material.slots().forEach { (name, style) ->
      assertEquals(if (name in display) fonts.display else fonts.ui, style.fontFamily, name)
    }
    assertEquals(fonts.display, type.message.fontFamily)
    assertEquals(fonts.display, type.reading.fontFamily)
  }

  @Test
  fun phoneWidthsUsePhoneSizesAndExpandedTheDenserOnes() {
    val fonts = bundledFonts()
    listOf(WidthClass.Compact, WidthClass.Medium).forEach { widthClass ->
      val type = lookType(fonts, widthClass).material
      assertEquals(16.sp, type.bodyLarge.fontSize)
      assertEquals(14.sp, type.bodyMedium.fontSize)
      assertEquals(12.sp, type.bodySmall.fontSize)
    }
    val expanded = lookType(fonts, WidthClass.Expanded).material
    assertEquals(13.sp, expanded.bodyLarge.fontSize)
    assertEquals(12.sp, expanded.bodyMedium.fontSize)
    assertEquals(11.sp, expanded.bodySmall.fontSize)
    assertEquals(30.sp, expanded.headlineLarge.fontSize)
  }
}
