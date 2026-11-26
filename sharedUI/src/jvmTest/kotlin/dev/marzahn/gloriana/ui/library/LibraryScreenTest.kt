package dev.marzahn.gloriana.ui.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.app_name
import dev.marzahn.gloriana.ui.resources.library_empty
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LibraryScreenTest {
  @Test
  fun emptyLibraryShowsTheAppNameAndTheEmptyLine() = runComposeUiTest {
    setContent { LibraryContent(LibraryState.Empty) }
    onNodeWithText(getString(Res.string.app_name)).assertExists()
    onNodeWithText(getString(Res.string.library_empty)).assertExists()
  }
}
