package dev.marzahn.gloriana.ui.presets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.marzahn.gloriana.logic.characters.DesktopPickedFile
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.Preset
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetImport
import dev.marzahn.gloriana.logic.presets.PresetImportResult
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.StoredPreset
import dev.marzahn.gloriana.ui.connections.LiveLibrary
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.rememberShellNavigator
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.entry_in_chat_title
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.WidthClass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val WAIT_MILLIS = 10_000L

private const val DEEP_PRESET =
  """{"prompts": [
    {"identifier": "main", "name": "Main Prompt", "role": "system", "content": "Be vivid."},
    {"identifier": "nsfw", "name": "Auxiliary Prompt", "role": "system", "content": "Stay in character.",
     "injection_position": 1, "injection_depth": 4}
  ],
  "prompt_order": [{"character_id": 100001, "order": [
    {"identifier": "main", "enabled": true}, {"identifier": "nsfw", "enabled": true}
  ]}]}"""

@OptIn(ExperimentalTestApi::class)
class PresetEntryScreenTest {
  private fun LiveLibrary.deepPreset(): Preset = runBlocking {
    val file = Files.createTempFile("gloriana-deep", ".json").apply { writeText(DEEP_PRESET) }
    val result = koin.get<PresetImport>().import(DesktopPickedFile(file))
    file.deleteIfExists()
    val id = assertIs<PresetImportResult.Imported>(result).id
    assertIs<StoredPreset.Readable>(koin.get<PresetRepository>().preset(id).first()).preset
  }

  @Test
  fun anEntryOpenedWithItsDraftOutsideTheChatGoesBackAtItsStoredDepth() = runComposeUiTest {
    LiveLibrary.use { library ->
      val stored = library.deepPreset()
      val entry = stored.content.entries.last()
      val sessions = library.koin.get<PresetSessions>()
      sessions.edit(stored.id, stored.content) { content ->
        content.copy(
          rows = content.rows.map { row ->
            if (row is PresetEntry && row.id == entry.id) row.copy(placement = EntryPlacement.Relative) else row
          },
        )
      }
      val freshPageStore = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
      }

      setContent {
        library.Show {
          CompositionLocalProvider(
            LocalShellNavigator provides rememberShellNavigator(),
            LocalViewModelStoreOwner provides freshPageStore,
          ) {
            Box(Modifier.size(400.dp, 900.dp)) {
              GlorianaTheme(WidthClass.of(400.dp)) { PresetEntryScreen(PresetEntryRoute(stored.id, entry.id)) }
            }
          }
        }
      }
      val inChat = hasText(runBlocking { getString(Res.string.entry_in_chat_title) }) and hasClickAction()
      waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(inChat).fetchSemanticsNodes().isNotEmpty() }
      onAllNodes(inChat).onFirst().performClick()
      waitForIdle()

      val shown = sessions.current(stored.id).content(stored.content).entries.last()
      assertEquals(EntryPlacement.InChat(4), shown.placement)
    }
  }
}
