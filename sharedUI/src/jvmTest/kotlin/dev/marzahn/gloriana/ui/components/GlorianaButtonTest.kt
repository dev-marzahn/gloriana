package dev.marzahn.gloriana.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_close
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.WidthClass
import kotlin.test.Test

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

  private companion object {
    const val TARGET = "target"
  }
}
