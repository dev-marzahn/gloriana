package dev.marzahn.gloriana.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GlorianaThemeTest {
  @Test
  fun darkModePicksTheDarkSetForTheLookAndMaterial() = runComposeUiTest {
    lateinit var colors: LookColors
    var materialSurface = Color.Unspecified
    setContent {
      GlorianaTheme(WidthClass.Compact, dark = true) {
        colors = GlorianaTheme.colors
        materialSurface = MaterialTheme.colorScheme.surface
      }
    }
    runOnIdle {
      assertEquals(DarkLookColors, colors)
      assertEquals(DarkLookColors.canvas, materialSurface)
    }
  }

  @Test
  fun lightModePicksTheLightSet() = runComposeUiTest {
    lateinit var colors: LookColors
    setContent { GlorianaTheme(WidthClass.Expanded, dark = false) { colors = GlorianaTheme.colors } }
    runOnIdle { assertEquals(LightLookColors, colors) }
  }
}
