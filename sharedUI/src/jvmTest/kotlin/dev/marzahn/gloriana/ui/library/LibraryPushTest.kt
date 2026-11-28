package dev.marzahn.gloriana.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.characters.DesktopPickedFile
import dev.marzahn.gloriana.ui.GlorianaApp
import dev.marzahn.gloriana.ui.characters.CharacterImports
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LibraryKind
import dev.marzahn.gloriana.ui.navigation.ShellNavigator
import dev.marzahn.gloriana.ui.navigation.rememberShellNavigator
import dev.marzahn.gloriana.ui.presets.PresetImports
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.library_kind_characters
import dev.marzahn.gloriana.ui.resources.preset_imported
import dev.marzahn.gloriana.ui.resources.snack_imported_one
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CARD = """{"spec": "chara_card_v2", "spec_version": "2.0", "data": {"name": "Oren"}}"""

private const val PRESET =
  """{"prompts": [{"identifier": "main", "name": "Main Prompt", "role": "system", "content": "Be vivid."}],
  "prompt_order": [{"character_id": 100001, "order": [{"identifier": "main", "enabled": true}]}]}"""

@OptIn(
  ExperimentalTestApi::class,
  ExperimentalPathApi::class,
  KoinExperimentalAPI::class,
  ExperimentalCoroutinesApi::class,
)
class LibraryPushTest {
  private val directory: Path = Files.createTempDirectory("gloriana-library-push")

  @AfterTest
  fun removeFiles() = directory.deleteRecursively()

  private fun file(name: String, text: String) = DesktopPickedFile(directory.resolve(name).also { it.writeText(text) })

  private fun ComposeUiTest.showApp(library: LiveLibrary): ShellNavigator {
    lateinit var navigator: ShellNavigator
    setContent {
      library.Show {
        navigator = rememberShellNavigator()
        Box(Modifier.size(400.dp, 700.dp)) {
          GlorianaApp(koinEntryProvider(), navigator = navigator)
        }
      }
    }
    waitForIdle()
    return navigator
  }

  private suspend fun ComposeUiTest.announcementOfAnImportDuringTheFadeToChats(
    library: LiveLibrary,
    navigator: ShellNavigator,
    import: () -> Unit,
  ): String {
    val announced = CompletableDeferred<AppMessage>()
    val listener = CoroutineScope(Dispatchers.Default).launch {
      announced.complete(library.koin.get<AppMessages>().messages.first())
    }
    mainClock.autoAdvance = false
    runOnIdle { navigator.select(Destination.Chats) }
    mainClock.advanceTimeBy(FADE_MIDWAY_MILLIS)
    onNodeWithText(getString(Res.string.library_kind_characters)).assertExists()
    import()
    waitUntil(timeoutMillis = WAIT_MILLIS) { announced.isCompleted }
    mainClock.autoAdvance = true
    waitForIdle()
    listener.cancel()
    return announced.getCompleted().text
  }

  @Test
  fun aCharacterImportFinishingWhileLibraryFadesOutAnnouncesItAndPushesNothing() = runComposeUiTest {
    LiveLibrary.use { library ->
      val navigator = showApp(library)
      val imports = library.koin.get<CharacterImports>()
      val announcement = announcementOfAnImportDuringTheFadeToChats(library, navigator) {
        imports.start(listOf(file("Oren.json", CARD)))
      }
      assertEquals(getString(Res.string.snack_imported_one, "Oren"), announcement)
      assertEquals(listOf(Destination.Library.root), navigator.stack(Destination.Library))
      assertEquals(Destination.Chats, navigator.current)
    }
  }

  @Test
  fun aPresetImportFinishingWhileLibraryFadesOutAnnouncesItAndPushesNothing() = runComposeUiTest {
    LiveLibrary.use { library ->
      val navigator = showApp(library)
      runOnIdle { navigator.showLibraryKind(LibraryKind.Presets) }
      waitForIdle()
      val imports = library.koin.get<PresetImports>()
      val announcement = announcementOfAnImportDuringTheFadeToChats(library, navigator) {
        imports.start(file("Tuned.json", PRESET))
      }
      assertEquals(getString(Res.string.preset_imported, "Tuned"), announcement)
      assertEquals(listOf(Destination.Library.root), navigator.stack(Destination.Library))
      assertEquals(Destination.Chats, navigator.current)
    }
  }

  private companion object {
    const val FADE_MIDWAY_MILLIS = 45L
    const val WAIT_MILLIS = 5_000L
  }
}
