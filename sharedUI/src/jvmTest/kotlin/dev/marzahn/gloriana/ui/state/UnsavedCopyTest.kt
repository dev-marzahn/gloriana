package dev.marzahn.gloriana.ui.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class Form(val name: String = "Work", val model: String = "model-1", val note: String = "")

private data class SetName(val name: String) : DraftEdit<Form> {
  override fun applied(to: Form) = to.copy(name = name)

  override fun replaces(earlier: DraftEdit<Form>) = earlier is SetName
}

private data class SetModel(val model: String) : DraftEdit<Form> {
  override fun applied(to: Form) = to.copy(model = model)
}

private data object AppendToNote : DraftEdit<Form> {
  override fun applied(to: Form) = to.copy(note = to.note + "!")
}

class UnsavedCopyTest {
  private val stored = Form()

  @Test
  fun anEditShowsInTheDraftAndCountsAsAChange() {
    val copy = UnsavedCopy.of(stored).edited(SetName("Home"))

    assertEquals("Home", copy.draft.name)
    assertTrue(copy.hasChanges)
  }

  @Test
  fun anEditBackToTheStoredValueIsNoChange() {
    val copy = UnsavedCopy.of(stored).edited(SetName("Home")).edited(SetName("Work"))

    assertFalse(copy.hasChanges)
  }

  @Test
  fun changesCompareWithTheGivenSameValue() {
    val copy = UnsavedCopy.of(stored) { one, other -> one.name.trim() == other.name.trim() }.edited(SetName("Work "))

    assertFalse(copy.hasChanges)
  }

  @Test
  fun aWriteFromElsewhereStaysUnderTheOpenEdits() {
    val copy = UnsavedCopy.of(stored).edited(SetName("Home")).storedNow(stored.copy(model = "model-2"))

    assertEquals(Form(name = "Home", model = "model-2"), copy.draft)
  }

  @Test
  fun discardingReturnsToTheStoredValue() {
    val copy = UnsavedCopy.of(stored).edited(SetName("Home")).discarded()

    assertEquals(stored, copy.draft)
    assertFalse(copy.hasChanges)
  }

  @Test
  fun typingIntoOneFieldKeepsOneEdit() {
    val typed = typing("Home", UnsavedCopy.of(stored)).edited(AppendToNote).edited(AppendToNote)

    assertEquals(listOf(SetName("Home"), AppendToNote, AppendToNote), typed.editsSinceSaveStarted)
    assertEquals(Form(name = "Home", note = "!!"), typed.draft)
  }

  @Test
  fun anEditDuringASaveIsNotFoldedIntoTheOneBeingWritten() {
    val saving = UnsavedCopy.of(stored).edited(SetName("Hom")).savingStarted().edited(SetName("Home"))

    assertEquals(listOf(SetName("Home")), saving.editsSinceSaveStarted)
  }

  @Test
  fun aSavedCopyShowsTheWrittenValueUntilTheStoredRowArrives() {
    val written = stored.copy(name = "Home")
    val saved = UnsavedCopy.of(stored).edited(SetName("Home")).savingStarted().saved(written)

    assertEquals(written, saved.draft)
    assertFalse(saved.hasChanges)
    assertFalse(saved.isSaving)
  }

  @Test
  fun editsMadeDuringTheSaveStayOverTheWrittenValue() {
    val written = stored.copy(name = "Home")
    val saved = UnsavedCopy.of(stored).edited(SetName("Home")).savingStarted().edited(SetModel("late")).saved(written)

    assertEquals(Form(name = "Home", model = "late"), saved.draft)
    assertTrue(saved.hasChanges)
  }

  @Test
  fun aStoredRowThatDiffersFromTheWrittenValueStillTakesOver() {
    val written = stored.copy(name = "Home ")
    val trimmed = stored.copy(name = "Home")
    val followed = UnsavedCopy.of(stored).edited(SetName("Home ")).savingStarted().saved(written).storedNow(trimmed)

    assertEquals(trimmed, followed.baseline)
    assertEquals(trimmed, followed.draft)
  }

  @Test
  fun theOldRowArrivingAgainKeepsTheWrittenValue() {
    val written = stored.copy(name = "Home")
    val saved = UnsavedCopy.of(stored).edited(SetName("Home")).savingStarted().saved(written).storedNow(stored)

    assertEquals(written, saved.baseline)
  }

  @Test
  fun aStoredRowThatArrivedDuringTheSaveIsTrustedOverTheWrittenValue() {
    val trimmed = stored.copy(name = "Home")
    val saved = UnsavedCopy.of(stored).edited(SetName("Home ")).savingStarted().storedNow(trimmed)
      .saved(stored.copy(name = "Home "))

    assertEquals(trimmed, saved.baseline)
  }

  @Test
  fun aFailedSaveKeepsEveryEdit() {
    val failed = UnsavedCopy.of(stored).edited(SetName("Home")).savingStarted().edited(SetModel("late")).notSaved()

    assertEquals(Form(name = "Home", model = "late"), failed.draft)
    assertFalse(failed.isSaving)
  }

  @Test
  fun aDiscardDuringTheSaveKeepsOnlyLaterEditsOverTheWrittenValue() {
    val written = stored.copy(name = "Home")
    val saved = UnsavedCopy.of(stored).edited(SetName("Home")).savingStarted().edited(SetModel("late")).discarded()
      .edited(SetModel("later")).saved(written)

    assertEquals(Form(name = "Home", model = "later"), saved.draft)
  }

  private fun typing(text: String, copy: UnsavedCopy<Form>) =
    text.indices.fold(copy) { typed, end -> typed.edited(SetName(text.take(end + 1))) }
}
