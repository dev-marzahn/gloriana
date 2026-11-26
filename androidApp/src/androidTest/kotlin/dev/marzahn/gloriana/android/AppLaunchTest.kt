package dev.marzahn.gloriana.android

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.library_empty
import org.jetbrains.compose.resources.getString
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AppLaunchTest {
  @Test
  fun opensToTheEmptyLibrary() = runAndroidComposeUiTest<MainActivity> {
    onNodeWithText(getString(Res.string.library_empty)).assertExists()
  }
}
