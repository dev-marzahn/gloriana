package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_close
import dev.marzahn.gloriana.ui.theme.DarkLookColors
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.LightLookColors
import dev.marzahn.gloriana.ui.theme.WidthClass
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GlorianaButtonTest {
  private val label = "label"

  @Test
  fun phoneButtonsDrawAt40dpInsideA48dpTouchTarget() = runComposeUiTest {
    setContent {
      GlorianaTheme(WidthClass.Compact) {
        GlorianaButton(label, {}, Modifier.testTag(TARGET), kind = ButtonKind.Primary)
      }
    }
    onNodeWithTag(TARGET).assertHeightIsEqualTo(48.dp)
    onNode(hasClickAction()).assertHeightIsEqualTo(40.dp)
  }

  @Test
  fun expandedSmallButtonsHaveNoTouchTargetPadding() = runComposeUiTest {
    setContent {
      GlorianaTheme(WidthClass.Expanded) {
        GlorianaButton(label, {}, Modifier.testTag(TARGET), size = ButtonSize.Small)
      }
    }
    onNodeWithTag(TARGET).assertHeightIsEqualTo(32.dp)
    onNode(hasClickAction()).assertHeightIsEqualTo(32.dp)
  }

  @Test
  fun phoneIconButtonsDrawAt40dpInsideA48dpTouchTarget() = runComposeUiTest {
    setContent {
      GlorianaTheme(WidthClass.Compact) {
        GlorianaIconButton(Res.drawable.ic_close, label, {}, Modifier.testTag(TARGET))
      }
    }
    onNodeWithTag(TARGET).assertHeightIsEqualTo(48.dp).assertWidthIsEqualTo(48.dp)
    onNode(hasClickAction()).assertHeightIsEqualTo(40.dp)
  }

  @Test
  fun enabledPrimaryButtonsCastNoShadowInLightMode() =
    assertPrimaryButtonCastsNoShadow(dark = false, canvas = LightLookColors.canvas)

  @Test
  fun enabledPrimaryButtonsCastNoShadowInDarkMode() =
    assertPrimaryButtonCastsNoShadow(dark = true, canvas = DarkLookColors.canvas)

  private fun assertPrimaryButtonCastsNoShadow(dark: Boolean, canvas: Color) = runComposeUiTest {
    setContent {
      GlorianaTheme(WidthClass.Compact, dark = dark) {
        Box(Modifier.background(canvas).padding(24.dp)) {
          GlorianaButton(label, {}, kind = ButtonKind.Primary)
        }
      }
    }
    val button = onNode(hasClickAction()).fetchSemanticsNode().boundsInRoot
    val pixels = onRoot().captureToImage().toPixelMap()
    val rings = (1..8).map { distance -> button.inflate(with(density) { distance.dp.toPx() }) }
    val shaded = rings.flatMap(::ringPixels).filter { (x, y) -> pixels[x, y] != canvas }
    assertEquals(emptyList(), shaded)
  }

  private fun ringPixels(ring: Rect): List<Pair<Int, Int>> {
    val left = ring.left.toInt()
    val top = ring.top.toInt()
    val right = ring.right.toInt()
    val bottom = ring.bottom.toInt()
    val rows = (left..right).flatMap { x -> listOf(x to top, x to bottom) }
    val columns = (top..bottom).flatMap { y -> listOf(left to y, right to y) }
    return rows + columns
  }

  private companion object {
    const val TARGET = "target"
  }
}
