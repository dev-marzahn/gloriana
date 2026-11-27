package dev.marzahn.gloriana.ui.presets

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private fun fitsInABundle(value: Any?): Boolean = when (value) {
  null, is String -> true
  is List<*> -> value.all(::fitsInABundle)
  is SnapshotMutableState<*> -> fitsInABundle(value.value)
  else -> false
}

@OptIn(ExperimentalTestApi::class)
class GroupPromptSaverTest {
  private fun recreated(prompt: GroupPrompt?): GroupPrompt? {
    var restoredPrompt: GroupPrompt? = null
    runComposeUiTest {
      var registry = SaveableStateRegistry(null, ::fitsInABundle)
      var activityAlive by mutableStateOf(true)
      var shown: MutableState<GroupPrompt?>? = null
      setContent {
        if (activityAlive) {
          CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { shown = rememberGroupPrompt() }
        }
      }
      runOnIdle { checkNotNull(shown).value = prompt }
      waitForIdle()

      val saved = registry.performSave()
      activityAlive = false
      waitForIdle()
      registry = SaveableStateRegistry(saved, ::fitsInABundle)
      activityAlive = true
      waitForIdle()
      restoredPrompt = checkNotNull(shown).value
    }
    return restoredPrompt
  }

  @Test
  fun anOpenAddPromptSurvivesARecreatedActivity() {
    assertEquals(GroupPrompt.Add, recreated(GroupPrompt.Add))
  }

  @Test
  fun anOpenRenamePromptSurvivesARecreatedActivityWithItsGroupAndName() {
    val rename = GroupPrompt.Rename(Uuid.random(), "Jailbreak, part 2")
    assertEquals(rename, recreated(rename))
  }

  @Test
  fun noOpenPromptComesBackClosed() {
    assertNull(recreated(null))
  }

  @Test
  fun aSavedValueWithAnUnknownTagRestoresNoPrompt() {
    assertNull(GroupPrompt.Saver.restore(listOf("rename", "only-two")))
    assertNull(GroupPrompt.Saver.restore(listOf("unknown")))
  }
}
