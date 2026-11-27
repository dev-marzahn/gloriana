package dev.marzahn.gloriana.ui.characters

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.characters.CharacterFields
import dev.marzahn.gloriana.logic.characters.DamagedAs
import dev.marzahn.gloriana.logic.characters.ExportFailure
import dev.marzahn.gloriana.logic.characters.ExportProblem
import dev.marzahn.gloriana.logic.characters.ImportFailure
import dev.marzahn.gloriana.logic.characters.KeptCardFields
import dev.marzahn.gloriana.logic.characters.Pronouns
import dev.marzahn.gloriana.ui.library.CharacterTile
import dev.marzahn.gloriana.ui.library.LibraryActions
import dev.marzahn.gloriana.ui.library.LibraryContent
import dev.marzahn.gloriana.ui.library.LibraryState
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.characters_error
import dev.marzahn.gloriana.ui.resources.create
import dev.marzahn.gloriana.ui.resources.edit
import dev.marzahn.gloriana.ui.resources.export
import dev.marzahn.gloriana.ui.resources.field_alternate_greeting
import dev.marzahn.gloriana.ui.resources.field_description
import dev.marzahn.gloriana.ui.resources.field_greeting
import dev.marzahn.gloriana.ui.resources.field_nickname
import dev.marzahn.gloriana.ui.resources.field_pronouns
import dev.marzahn.gloriana.ui.resources.field_tags
import dev.marzahn.gloriana.ui.resources.import_failed_some
import dev.marzahn.gloriana.ui.resources.import_importing
import dev.marzahn.gloriana.ui.resources.import_waiting
import dev.marzahn.gloriana.ui.resources.library_empty
import dev.marzahn.gloriana.ui.resources.page_error
import dev.marzahn.gloriana.ui.resources.pronouns_not_set
import dev.marzahn.gloriana.ui.resources.reason_damaged_png
import dev.marzahn.gloriana.ui.resources.reason_not_a_card
import dev.marzahn.gloriana.ui.resources.retry
import dev.marzahn.gloriana.ui.resources.start_chat
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.WidthClass
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CharacterScreensTest {
  private val phone = 400.dp
  private val desktop = 1100.dp

  private fun ComposeUiTest.show(width: Dp, content: @Composable () -> Unit) {
    setContent {
      Box(Modifier.size(width, 900.dp)) {
        GlorianaTheme(WidthClass.of(width)) { content() }
      }
    }
  }

  private fun tiles(vararg names: String) =
    names.mapIndexed { index, name -> CharacterTile(CharacterIds[index], name, avatarPath = null) }

  @Test
  fun `an empty library offers Create`() = runComposeUiTest {
    var created = false
    show(phone) { LibraryContent(LibraryState.Empty, actions = LibraryActions(onCreate = { created = true })) }
    onNodeWithText(getString(Res.string.library_empty)).assertIsDisplayed()
    onNodeWithText(getString(Res.string.create)).performClick()
    assertTrue(created)
  }

  @Test
  fun `a library that can't be read says so with Retry`() = runComposeUiTest {
    var retried = false
    show(phone) { LibraryContent(LibraryState.CouldNotLoad, actions = LibraryActions(onRetry = { retried = true })) }
    onNodeWithText(getString(Res.string.characters_error)).assertIsDisplayed()
    onNodeWithText(getString(Res.string.retry)).performClick()
    assertTrue(retried)
  }

  @Test
  fun `the grid shows three tiles to a row on a phone and in the desktop list pane`() {
    listOf(phone, desktop).forEach { width ->
      runComposeUiTest {
        show(width) { LibraryContent(LibraryState.Shown(tiles("Ada", "Bo", "Cy", "Di"), ImportState())) }
        val first = onNodeWithText("Ada").getBoundsInRoot()
        val third = onNodeWithText("Cy").getBoundsInRoot()
        val fourth = onNodeWithText("Di").getBoundsInRoot()
        assertEquals(first.top, third.top, "three to a row at $width")
        assertTrue(fourth.top > first.top, "the fourth starts the next row at $width")
      }
    }
  }

  @Test
  fun `a tap on a tile reports its character`() = runComposeUiTest {
    var tapped: kotlin.uuid.Uuid? = null
    show(phone) {
      val actions = LibraryActions(onTile = { tapped = it })
      LibraryContent(LibraryState.Shown(tiles("Ada"), ImportState()), actions = actions)
    }
    onNodeWithText("Ada").performClick()
    assertEquals(CharacterIds[0], tapped)
  }

  @Test
  fun `running tiles say which file imports and which wait`() = runComposeUiTest {
    val running = listOf(
      RunningFile(1, "one.png", RunningStatus.Importing, 1, 2),
      RunningFile(2, "two.json", RunningStatus.Waiting, 2, 2),
    )
    show(phone) { LibraryContent(LibraryState.Shown(emptyList(), ImportState(running = running))) }
    onNodeWithText("one.png").assertIsDisplayed()
    onNodeWithText(getString(Res.string.import_importing)).assertIsDisplayed()
    onNodeWithText(getString(Res.string.import_waiting)).assertIsDisplayed()
    assertEquals(1, onAllNodesWithText(getString(Res.string.create)).fetchSemanticsNodes().size, "no Create tile")
  }

  @Test
  fun `failures are listed together above the grid`() = runComposeUiTest {
    val note = ImportNote.Failed(
      picked = 3,
      failures = listOf(
        FailedFile("broken.png", ImportFailure.Damaged(DamagedAs.Png)),
        FailedFile("notes.txt", ImportFailure.NotACharacterCard),
      ),
    )
    show(phone) { LibraryContent(LibraryState.Shown(tiles("Ada"), ImportState(note = note))) }
    onNodeWithText(getPluralString(Res.plurals.import_failed_some, 2, 2, 3)).assertIsDisplayed()
    onNodeWithText(getString(Res.string.reason_damaged_png, "broken.png")).assertIsDisplayed()
    onNodeWithText(getString(Res.string.reason_not_a_card, "notes.txt"), substring = true).assertIsDisplayed()
  }

  private val fullCharacter = character(
    fields = CharacterFields(
      name = "Tamsin",
      creator = "Wren",
      nickname = "Tam",
      characterVersion = "2",
      description = "A **sailor** from the coast.",
      firstMessage = "",
      alternateGreetings = listOf("Ahoy!"),
    ),
    kept = KeptCardFields(tags = listOf("sea", "adventure")),
  )

  private fun shown(character: dev.marzahn.gloriana.logic.characters.Character, export: ExportState? = null) =
    CharacterPageState.Shown(character, avatarPath = null, export = export, delete = DeleteProgress.Idle)

  @Test
  fun `the page shows filled fields in order and hides empty ones`() = runComposeUiTest {
    show(phone) { CharacterContent(shown(fullCharacter), PageActions()) }
    onNodeWithText("by Wren · version 2").assertIsDisplayed()
    val nickname = onNodeWithText(getString(Res.string.field_nickname)).getBoundsInRoot()
    val pronouns = onNodeWithText(getString(Res.string.field_pronouns)).getBoundsInRoot()
    val description = onNodeWithText(getString(Res.string.field_description)).getBoundsInRoot()
    val alternate = onNodeWithText(getString(Res.string.field_alternate_greeting, 1)).getBoundsInRoot()
    assertTrue(nickname.top < pronouns.top && pronouns.top < description.top && description.top < alternate.top)
    onNodeWithText(getString(Res.string.field_greeting)).assertDoesNotExist()
    onNodeWithText(getString(Res.string.pronouns_not_set)).assertExists()
    onNodeWithText(getString(Res.string.field_tags)).assertExists()
    onNodeWithText("sea, adventure").assertExists()
  }

  @Test
  fun `set pronouns show their forms`() = runComposeUiTest {
    val withPronouns = character(pronouns = Pronouns(subject = "she", objective = "her"))
    show(phone) { CharacterContent(shown(withPronouns), PageActions()) }
    onNodeWithText("she / her").assertIsDisplayed()
  }

  @Test
  fun `later actions are drawn but not available`() = runComposeUiTest {
    show(phone) { CharacterContent(shown(fullCharacter), PageActions()) }
    onNodeWithContentDescription(getString(Res.string.edit)).assertIsNotEnabled()
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
    onNodeWithText(getString(Res.string.start_chat)).assertIsNotEnabled()
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
  }

  @Test
  fun `a wide window has Export as the page's only action`() = runComposeUiTest {
    show(desktop) { CharacterContent(shown(fullCharacter), PageActions()) }
    onNodeWithText(getString(Res.string.start_chat)).assertDoesNotExist()
    onNodeWithText(getString(Res.string.export)).assertIsDisplayed()
  }

  @Test
  fun `a running export disables Export and says which file it writes`() = runComposeUiTest {
    show(phone) { CharacterContent(shown(fullCharacter, ExportState.Running("Tamsin.png")), PageActions()) }
    onNode(hasText(getString(Res.string.export)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
      .assertExists()
    onNodeWithText("Exporting Tamsin.png…").assertIsDisplayed()
  }

  @Test
  fun `an export that wasn't saved says why`() = runComposeUiTest {
    val problem = ExportProblem(ExportFailure.StorageFull, incompleteFileLeft = false)
    val failed = ExportState.NotSaved("Tamsin.png", problem)
    show(phone) { CharacterContent(shown(fullCharacter, failed), PageActions()) }
    onNodeWithText("Tamsin.png wasn't saved:", substring = true).assertIsDisplayed()
  }

  @Test
  fun `a page that can't be read offers Retry`() = runComposeUiTest {
    show(phone) { CharacterContent(CharacterPageState.CouldNotLoad, PageActions()) }
    onNodeWithText(getString(Res.string.page_error), substring = true).assertIsDisplayed()
  }
}
