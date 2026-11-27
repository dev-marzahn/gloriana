package dev.marzahn.gloriana.ui.presets

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DraftSyncTest {
  private val route = PresetEntryRoute("00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002")

  private fun fieldAfter(steps: List<Pair<String, String?>>): String {
    var text = ""
    runComposeUiTest {
      var shown by mutableStateOf(steps.first().first)
      var draft by mutableStateOf(steps.first().second)
      var field: MutableState<String>? = null
      setContent { field = rememberDraftText(route, shown, draftNow = { draft }) }
      steps.drop(1).forEach { (nextShown, nextDraft) ->
        waitForIdle()
        draft = nextDraft
        shown = nextShown
      }
      waitForIdle()
      text = checkNotNull(field).value
    }
    return text
  }

  @Test
  fun aFieldFirstShownWhileLoadingTakesTheLoadedValueWhenTheDraftHasNone() {
    assertEquals("4", fieldAfter(listOf("0" to null, "4" to null)))
  }

  @Test
  fun aLoadedDraftValueWinsOverTheShownOne() {
    assertEquals("7", fieldAfter(listOf("0" to null, "4" to "7")))
  }

  @Test
  fun aDiscardedDraftResyncsTheFieldToTheShownValue() {
    assertEquals("4", fieldAfter(listOf("4" to "4", "9" to "9", "4" to null)))
  }
}
