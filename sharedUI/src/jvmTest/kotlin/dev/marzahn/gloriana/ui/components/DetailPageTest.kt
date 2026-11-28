package dev.marzahn.gloriana.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_hub
import dev.marzahn.gloriana.ui.resources.saving
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.WidthClass
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

private val Phone = 400.dp
private val Desktop = 1280.dp

@OptIn(ExperimentalTestApi::class)
class DetailPageTest {
  private fun ComposeUiTest.page(width: Dp, shown: BackButtonShown, content: @Composable () -> Unit = {}) {
    setContent {
      GlorianaTheme(WidthClass.of(width)) {
        DetailPage(PageBack("All Presets", onBack = {}, shown)) { content() }
      }
    }
  }

  private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

  @Test
  fun theBackButtonShowsBelowTheWorkbench() = runComposeUiTest {
    page(Phone, BackButtonShown.BelowTheWorkbench)

    assertEquals(1, count("All Presets"))
  }

  @Test
  fun theWorkbenchHidesTheBackButtonUnlessThePageAsksForItOnEveryWidth() {
    runComposeUiTest {
      page(Desktop, BackButtonShown.BelowTheWorkbench)
      assertEquals(0, count("All Presets"))
    }
    runComposeUiTest {
      page(Desktop, BackButtonShown.OnEveryWidth)
      assertEquals(1, count("All Presets"))
    }
  }

  @Test
  fun anUnnamedPageShowsItsPlaceholder() = runComposeUiTest {
    page(Phone, BackButtonShown.BelowTheWorkbench) {
      val name = PageName.of("  ", placeholder = "New Connection")
      DetailHeader(Res.drawable.ic_hub, name, meta = { Text("Not saved yet") })
    }

    assertEquals(1, count("New Connection"))
  }

  @Test
  fun aSavingPageReadsSavingAndCantSaveAgain() = runComposeUiTest {
    page(Phone, BackButtonShown.BelowTheWorkbench) { SaveButton(saving = true, enabled = false, onSave = {}) }

    onNodeWithText(runBlocking { getString(Res.string.saving) }).assertIsNotEnabled()
  }
}
