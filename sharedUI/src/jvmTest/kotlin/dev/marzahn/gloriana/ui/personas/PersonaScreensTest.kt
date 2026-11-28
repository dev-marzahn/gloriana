package dev.marzahn.gloriana.ui.personas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.marzahn.gloriana.logic.personas.PersonaWritePoint
import dev.marzahn.gloriana.logic.storage.ChatSetting
import dev.marzahn.gloriana.logic.storage.UuidV7IdFactory
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.components.SKELETON_TAG
import dev.marzahn.gloriana.ui.components.SKELETON_WAIT_MILLIS
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.Opening
import dev.marzahn.gloriana.ui.navigation.rememberShellNavigator
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.back_to_personas
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.changes_not_saved
import dev.marzahn.gloriana.ui.resources.create_persona
import dev.marzahn.gloriana.ui.resources.delete
import dev.marzahn.gloriana.ui.resources.delete_persona
import dev.marzahn.gloriana.ui.resources.delete_failed
import dev.marzahn.gloriana.ui.resources.delete_persona_chats
import dev.marzahn.gloriana.ui.resources.deleting
import dev.marzahn.gloriana.ui.resources.destination_library
import dev.marzahn.gloriana.ui.resources.discard
import dev.marzahn.gloriana.ui.resources.discard_new_persona
import dev.marzahn.gloriana.ui.resources.discard_persona_title
import dev.marzahn.gloriana.ui.resources.edit
import dev.marzahn.gloriana.ui.resources.library_kind_personas
import dev.marzahn.gloriana.ui.resources.library_kind_presets
import dev.marzahn.gloriana.ui.resources.make_default
import dev.marzahn.gloriana.ui.resources.more_for
import dev.marzahn.gloriana.ui.resources.not_made_default
import dev.marzahn.gloriana.ui.resources.not_saved
import dev.marzahn.gloriana.ui.resources.not_saved_storage
import dev.marzahn.gloriana.ui.resources.not_saved_yet
import dev.marzahn.gloriana.ui.resources.persona_default_cant_delete
import dev.marzahn.gloriana.ui.resources.persona_delete_text_no_chats
import dev.marzahn.gloriana.ui.resources.persona_error
import dev.marzahn.gloriana.ui.resources.persona_no_name
import dev.marzahn.gloriana.ui.resources.persona_not_saved_storage_detail
import dev.marzahn.gloriana.ui.resources.persona_refused_default
import dev.marzahn.gloriana.ui.resources.personas_error
import dev.marzahn.gloriana.ui.resources.personas_nothing_picked
import dev.marzahn.gloriana.ui.resources.retry
import dev.marzahn.gloriana.ui.resources.save
import dev.marzahn.gloriana.ui.resources.saving
import dev.marzahn.gloriana.ui.resources.snack_deleted
import dev.marzahn.gloriana.ui.resources.snack_made_default_persona
import dev.marzahn.gloriana.ui.resources.snack_saved
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import dev.marzahn.gloriana.ui.testing.rememberUnstoredMoves
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.WidthClass
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WAIT_MILLIS = 10_000L

private val EXPANDED_WIDTH = 1200.dp
private val COMPACT_WIDTH = 400.dp
private val WINDOW_HEIGHT = 900.dp

private const val NAME_FIELD = 0
private const val DESCRIPTION_FIELD = 1

@OptIn(ExperimentalTestApi::class)
class PersonaScreensTest {
  private class BackInput : NavigationEventInput() {
    fun back() = dispatchOnBackCompleted()
  }

  private val backInput = BackInput()

  private suspend fun ComposeUiTest.openPersonas(library: LiveLibrary, width: Dp = EXPANDED_WIDTH) {
    setContent {
      val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
      remember { dispatcher.addInput(backInput) }
      library.Show { Box(Modifier.size(width, WINDOW_HEIGHT)) { App() } }
    }
    val destination = getString(Res.string.destination_library)
    tap((hasText(destination) or hasContentDescription(destination)) and hasClickAction())
    tap(hasText(getString(Res.string.library_kind_personas)) and hasClickAction())
    waitFor(hasText(library.defaultPersona().fields.name))
    waitForIdle()
  }

  private suspend fun ComposeUiTest.openPersona(library: LiveLibrary, name: String, width: Dp = EXPANDED_WIDTH) {
    openPersonas(library, width)
    tap(hasText(name) and hasClickAction())
    waitFor(hasContentDescription(getString(Res.string.more_for, name)))
  }

  private suspend fun ComposeUiTest.tap(text: StringResource) = tap(hasText(getString(text)) and hasClickAction())

  private fun ComposeUiTest.tap(matcher: SemanticsMatcher) {
    waitFor(matcher)
    onAllNodes(matcher).onFirst().performClick()
  }

  private fun ComposeUiTest.waitFor(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun ComposeUiTest.waitForNone(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
  }

  private fun ComposeUiTest.type(field: Int, text: String) {
    waitFor(hasSetTextAction())
    onAllNodes(hasSetTextAction())[field].performTextReplacement(text)
  }

  private fun shown(text: String) = hasText(text, substring = true)

  @Test
  fun theListShowsEveryPersonaWithItsLabelAndMarksTheDefault() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren", label = "Work")
      openPersonas(library)

      waitFor(hasText("Wren"))
      waitFor(hasText("Work"))
      waitFor(hasText(getString(Res.string.create_persona)) and hasClickAction())
    }
  }

  @Test
  fun theListStartsWithTheShippedDefaultAndANewPersonaJoinsTheEnd() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Alba")
      openPersonas(library)

      waitFor(hasText("Alba"))
      val shippedTop = onAllNodes(hasText(library.defaultPersona().fields.name)).onFirst().fetchSemanticsNode()
      val albaTop = onAllNodes(hasText("Alba")).onFirst().fetchSemanticsNode()
      assertTrue(shippedTop.boundsInRoot.top < albaTop.boundsInRoot.top)
    }
  }

  @Test
  fun theEditingStateHasNoHeaderButtons() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.edit)))
      waitFor(hasText(getString(Res.string.save)) and hasClickAction())

      waitForNone(hasContentDescription(getString(Res.string.more_for, "Wren")))
      assertTrue(onAllNodes(hasContentDescription(getString(Res.string.edit))).fetchSemanticsNodes().isEmpty())
    }
  }

  @Test
  fun aNewPersonaAsksForANameThenSavesAndShowsItReadOnly() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPersonas(library)
      tap(Res.string.create_persona)
      waitFor(hasText(getString(Res.string.not_saved_yet)))
      onAllNodes(hasText(getString(Res.string.save)) and hasClickAction()).onFirst().assertIsEnabled()

      tap(Res.string.save)
      waitFor(hasText(getString(Res.string.persona_no_name)))
      type(NAME_FIELD, "Wren")
      waitForNone(hasText(getString(Res.string.persona_no_name)))
      tap(Res.string.save)

      waitFor(hasText(getString(Res.string.snack_saved, "Wren")))
      waitFor(hasContentDescription(getString(Res.string.edit)))
      assertTrue(library.personas().any { it.fields.name == "Wren" })
    }
  }

  @Test
  fun anEditedPersonaSavesItsFieldsAndGoesBackToReadOnly() = runComposeUiTest {
    LiveLibrary.use { library ->
      val wren = library.createdPersona("Wren")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.edit)))
      onAllNodes(hasText(getString(Res.string.save)) and hasClickAction()).onFirst().assertIsNotEnabled()

      type(DESCRIPTION_FIELD, "A quiet archivist.")
      tap(Res.string.save)

      waitFor(hasText(getString(Res.string.snack_saved, "Wren")))
      waitFor(hasContentDescription(getString(Res.string.edit)))
      val stored = library.personas().single { it.id == wren.id }
      assertEquals("A quiet archivist.", stored.fields.description)
    }
  }

  @Test
  fun aFailedSaveKeepsWhatWasTypedAndSaysSo() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.edit)))
      type(DESCRIPTION_FIELD, "Kept")
      library.failingWrite = PersonaWritePoint.UpdateRowWritten
      tap(Res.string.save)

      waitFor(shown(getString(Res.string.not_saved, "Wren")))
      waitFor(hasText("Kept"))
    }
  }

  @Test
  fun cancellingChangesAsksThenDiscardsThem() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.edit)))
      type(DESCRIPTION_FIELD, "Never kept")
      tap(Res.string.cancel)

      waitFor(hasText(getString(Res.string.changes_not_saved, "Wren")))
      tap(Res.string.discard)

      waitFor(hasContentDescription(getString(Res.string.edit)))
      waitForNone(hasText("Never kept"))
    }
  }

  @Test
  fun cancellingANewPersonaWithANameAsksToDiscardIt() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPersonas(library)
      tap(Res.string.create_persona)
      type(NAME_FIELD, "Draft")
      tap(Res.string.cancel)

      waitFor(hasText(getString(Res.string.discard_persona_title)))
      waitFor(hasText(getString(Res.string.discard_new_persona, "Draft")))
      tap(Res.string.discard)

      waitForNone(hasText(getString(Res.string.not_saved_yet)))
      assertTrue(library.personas().none { it.fields.name == "Draft" })
    }
  }

  @Test
  fun anEmptyNewPersonaClosesWithoutAsking() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPersonas(library)
      tap(Res.string.create_persona)
      waitFor(hasText(getString(Res.string.not_saved_yet)))
      tap(Res.string.cancel)

      waitForNone(hasText(getString(Res.string.not_saved_yet)))
      assertTrue(onAllNodes(hasText(getString(Res.string.discard_persona_title))).fetchSemanticsNodes().isEmpty())
    }
  }

  @Test
  fun makeDefaultMovesTheDefaultAndIsHiddenOnTheDefault() = runComposeUiTest {
    LiveLibrary.use { library ->
      val wren = library.createdPersona("Wren")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.more_for, "Wren")))
      tap(Res.string.make_default)

      waitFor(hasText(getString(Res.string.snack_made_default_persona, "Wren")))
      waitUntil(timeoutMillis = WAIT_MILLIS) { library.defaultPersona().id == wren.id }
      tap(hasContentDescription(getString(Res.string.more_for, "Wren")))
      waitFor(hasText(getString(Res.string.persona_default_cant_delete)))
      assertTrue(onAllNodes(hasText(getString(Res.string.make_default))).fetchSemanticsNodes().isEmpty())
      onAllNodes(hasText(getString(Res.string.delete_persona)) and hasClickAction()).onFirst().assertIsNotEnabled()
    }
  }

  @Test
  fun theDeleteQuestionCountsTheChatsThatMoveToTheDefault() = runComposeUiTest {
    LiveLibrary.use { library ->
      val wren = library.createdPersona("Wren")
      library.chatsUsing[ChatSetting.Persona to wren.id] = 2
      val defaultName = library.defaultPersona().fields.name
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.more_for, "Wren")))
      tap(Res.string.delete_persona)

      waitFor(hasText(getPluralString(Res.plurals.delete_persona_chats, 2, 2, defaultName)))
      tap(Res.string.delete)

      waitFor(hasText(getString(Res.string.snack_deleted, "Wren")))
      waitUntil(timeoutMillis = WAIT_MILLIS) { library.personas().none { it.id == wren.id } }
    }
  }

  @Test
  fun aPersonaNoChatUsesSaysSo() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.more_for, "Wren")))
      tap(Res.string.delete_persona)

      waitFor(hasText(getString(Res.string.persona_delete_text_no_chats, "Wren")))
    }
  }

  @Test
  fun aPersonaMadeTheDefaultBeforeItsDeleteIsRefusedWithoutRetry() = runComposeUiTest {
    LiveLibrary.use { library ->
      val wren = library.createdPersona("Wren")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.more_for, "Wren")))
      tap(Res.string.delete_persona)
      waitFor(hasText(getString(Res.string.delete)) and hasClickAction())
      runBlocking { library.personaRepository.makeDefault(wren.id) }
      tap(Res.string.delete)

      waitFor(shown(getString(Res.string.persona_refused_default, "Wren")))
      assertTrue(onAllNodes(hasText(getString(Res.string.retry))).fetchSemanticsNodes().isEmpty())
    }
  }

  @Test
  fun aPersonaPageLoadsOnAFreshViewModelStore() = runComposeUiTest {
    LiveLibrary.use { library ->
      val wren = library.createdPersona("Wren")
      showAlone(library) { PersonaScreen(PersonaRoute.Saved(wren.id)) }

      waitFor(hasContentDescription(getString(Res.string.more_for, "Wren")))
    }
  }

  @Test
  fun aNewPersonaPageShowsAtOnceOnAFreshViewModelStore() = runComposeUiTest {
    LiveLibrary.use { library ->
      showAlone(library) { PersonaScreen(PersonaRoute.New(Opening.fresh(UuidV7IdFactory))) }

      waitFor(hasText(getString(Res.string.not_saved_yet)))
    }
  }

  @Test
  fun theListShowsItsSkeletonOnlyAfterAShortWait() = runComposeUiTest {
    LiveLibrary.use { library ->
      mainClock.autoAdvance = false
      showAlone(library) {
        PersonasContent(PersonasListing.Loading, PersonasActions(), null, {}, rememberUnstoredMoves())
      }

      onNodeWithTag(SKELETON_TAG).assertDoesNotExist()
      mainClock.advanceTimeBy(SKELETON_WAIT_MILLIS + 1)
      onNodeWithTag(SKELETON_TAG).assertExists()
    }
  }

  @Test
  fun aLoadingPageShowsItsSkeletonOnlyAfterAShortWait() = runComposeUiTest {
    LiveLibrary.use { library ->
      mainClock.autoAdvance = false
      showAlone(library) {
        PersonaPageContent(PersonaPageState.Loading, PersonaPageActions(), remember { FocusRequester() })
      }

      onNodeWithTag(SKELETON_TAG).assertDoesNotExist()
      mainClock.advanceTimeBy(SKELETON_WAIT_MILLIS + 1)
      onNodeWithTag(SKELETON_TAG).assertExists()
    }
  }

  @Test
  fun aListThatCouldNotLoadIsRetriedIntoItsPersonas() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      library.makeUnreadable("library_settings")
      setContent { library.Show { App() } }
      tap(hasContentDescription(getString(Res.string.destination_library)) and hasClickAction())
      tap(hasText(getString(Res.string.library_kind_personas)) and hasClickAction())

      waitFor(shown(getString(Res.string.personas_error)))
      library.makeReadableAgain("library_settings")
      tap(Res.string.retry)

      waitFor(hasText("Wren"))
      waitForNone(shown(getString(Res.string.personas_error)))
    }
  }

  @Test
  fun aPageThatCouldNotLoadIsRetriedIntoThePersona() = retriesAPageWithUnreadable("personas")

  @Test
  fun aPageThatCouldNotReadTheDefaultIsRetriedIntoThePersona() = retriesAPageWithUnreadable("library_settings")

  private fun retriesAPageWithUnreadable(table: String) = runComposeUiTest {
    LiveLibrary.use { library ->
      val wren = library.createdPersona("Wren")
      library.makeUnreadable(table)
      showAlone(library) { PersonaScreen(PersonaRoute.Saved(wren.id)) }

      waitFor(shown(getString(Res.string.persona_error)))
      library.makeReadableAgain(table)
      tap(Res.string.retry)

      waitFor(hasContentDescription(getString(Res.string.more_for, "Wren")))
    }
  }

  @Test
  fun aFailedMakeDefaultSaysSoAndIsRetried() = runComposeUiTest {
    LiveLibrary.use { library ->
      val wren = library.createdPersona("Wren")
      openPersona(library, "Wren")
      library.refuseChangesTo("library_settings")
      tap(hasContentDescription(getString(Res.string.more_for, "Wren")))
      tap(Res.string.make_default)

      waitFor(shown(getString(Res.string.not_made_default, "Wren")))
      library.allowChangesAgain()
      tap(Res.string.retry)

      waitFor(hasText(getString(Res.string.snack_made_default_persona, "Wren")))
      waitUntil(timeoutMillis = WAIT_MILLIS) { library.defaultPersona().id == wren.id }
    }
  }

  @Test
  fun aFailedDeleteSaysSoAndIsRetried() = runComposeUiTest {
    LiveLibrary.use { library ->
      val wren = library.createdPersona("Wren")
      openPersona(library, "Wren")
      library.failingWrite = PersonaWritePoint.DeleteRowRemoved
      tap(hasContentDescription(getString(Res.string.more_for, "Wren")))
      tap(Res.string.delete_persona)
      tap(Res.string.delete)

      waitFor(shown(getString(Res.string.delete_failed, "Wren")))
      library.failingWrite = null
      tap(Res.string.retry)
      tap(Res.string.delete)

      waitFor(hasText(getString(Res.string.snack_deleted, "Wren")))
      waitUntil(timeoutMillis = WAIT_MILLIS) { library.personas().none { it.id == wren.id } }
    }
  }

  @Test
  fun aSavingPersonaSaysSoAndCannotBeSavedOrCancelledAgain() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.edit)))
      type(DESCRIPTION_FIELD, "Changed")
      library.writes.hold()
      tap(Res.string.save)

      waitFor(hasText(getString(Res.string.saving)))
      onAllNodes(hasText(getString(Res.string.saving)) and hasClickAction()).onFirst().assertIsNotEnabled()
      onAllNodes(hasText(getString(Res.string.cancel)) and hasClickAction()).onFirst().assertIsNotEnabled()
      library.writes.release()

      waitFor(hasText(getString(Res.string.snack_saved, "Wren")))
    }
  }

  @Test
  fun aSaveRefusedForStorageSaysToFreeSpace() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.edit)))
      type(DESCRIPTION_FIELD, "Kept")
      library.storageFullAt = PersonaWritePoint.UpdateRowWritten
      tap(Res.string.save)

      waitFor(shown(getString(Res.string.not_saved_storage, "Wren")))
      waitFor(shown(getString(Res.string.persona_not_saved_storage_detail)))
      waitFor(hasText("Kept"))
    }
  }

  @Test
  fun aDeletedPersonaClosesItsPageAndFocusMovesToTheNextRow() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      library.createdPersona("Wren Hale")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.more_for, "Wren")))
      tap(Res.string.delete_persona)
      tap(Res.string.delete)

      waitFor(hasText(getString(Res.string.snack_deleted, "Wren")))
      waitForNone(hasContentDescription(getString(Res.string.more_for, "Wren")))
      waitUntil(timeoutMillis = WAIT_MILLIS) {
        onAllNodes(hasText("Wren Hale") and hasClickAction() and isFocused()).fetchSemanticsNodes().isNotEmpty()
      }
    }
  }

  @Test
  fun aDeletingPersonaSaysSoWithBothButtonsDisabled() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.more_for, "Wren")))
      tap(Res.string.delete_persona)
      library.writes.hold()
      tap(Res.string.delete)

      waitFor(hasText(getString(Res.string.deleting)))
      onAllNodes(hasText(getString(Res.string.deleting)) and hasClickAction()).onFirst().assertIsNotEnabled()
      onAllNodes(hasText(getString(Res.string.cancel)) and hasClickAction()).onFirst().assertIsNotEnabled()
      library.writes.release()

      waitFor(hasText(getString(Res.string.snack_deleted, "Wren")))
    }
  }

  private suspend fun ComposeUiTest.changeWren(library: LiveLibrary, width: Dp = EXPANDED_WIDTH) {
    library.createdPersona("Wren")
    library.createdPersona("Alba")
    openPersona(library, "Wren", width)
    tap(hasContentDescription(getString(Res.string.edit)))
    type(DESCRIPTION_FIELD, "Never kept")
  }

  private suspend fun ComposeUiTest.discardsWhenAsked() {
    waitFor(hasText(getString(Res.string.changes_not_saved, "Wren")))
    tap(Res.string.discard)
    waitForNone(hasText("Never kept"))
  }

  @Test
  fun leavingByThePageBackAsksAboutTheChanges() = runComposeUiTest {
    LiveLibrary.use { library ->
      changeWren(library, COMPACT_WIDTH)
      val back = getString(Res.string.back_to_personas)
      tap((hasText(back) or hasContentDescription(back)) and hasClickAction())

      discardsWhenAsked()
      waitFor(hasText(getString(Res.string.create_persona)) and hasClickAction())
    }
  }

  @Test
  fun leavingByTheBackEventEscapeSendsAsksAboutTheChanges() = runComposeUiTest {
    LiveLibrary.use { library ->
      changeWren(library, COMPACT_WIDTH)
      runOnIdle { backInput.back() }

      discardsWhenAsked()
    }
  }

  @Test
  fun leavingByTheKindPickerAsksAboutTheChanges() = runComposeUiTest {
    LiveLibrary.use { library ->
      changeWren(library)
      tap(hasText(getString(Res.string.library_kind_presets)) and hasClickAction())

      discardsWhenAsked()
    }
  }

  @Test
  fun leavingForAnotherRowAsksAboutTheChanges() = runComposeUiTest {
    LiveLibrary.use { library ->
      changeWren(library)
      tap(hasText("Alba") and hasClickAction())

      discardsWhenAsked()
      waitFor(hasContentDescription(getString(Res.string.more_for, "Alba")))
    }
  }

  @Test
  fun afterADeleteOnExpandedWidthNothingIsPicked() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersona(library, "Wren")
      tap(hasContentDescription(getString(Res.string.more_for, "Wren")))
      tap(Res.string.delete_persona)
      tap(Res.string.delete)

      waitFor(hasText(getString(Res.string.personas_nothing_picked)))
    }
  }

  @Test
  fun createOnExpandedWidthLeavesNoRowSelectedAndTheFirstSaveSelectsTheNewRow() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Alba")
      openPersona(library, "Alba")
      waitFor(hasText("Alba") and isSelected())
      tap(Res.string.create_persona)
      waitFor(hasText(getString(Res.string.not_saved_yet)))

      waitForNone(hasText("Alba") and isSelected())
      type(NAME_FIELD, "Wren")
      tap(Res.string.save)

      waitFor(hasText("Wren") and isSelected())
    }
  }

  private fun ComposeUiTest.showAlone(library: LiveLibrary, content: @Composable () -> Unit) {
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
            GlorianaTheme(WidthClass.of(400.dp), content = content)
          }
        }
      }
    }
  }
}
