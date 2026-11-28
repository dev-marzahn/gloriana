package dev.marzahn.gloriana.ui.characters

import dev.marzahn.gloriana.logic.characters.CharacterFields
import dev.marzahn.gloriana.logic.characters.CharacterNote
import dev.marzahn.gloriana.logic.characters.DamagedAs
import dev.marzahn.gloriana.logic.characters.FileImportResult
import dev.marzahn.gloriana.logic.characters.ImportFailure
import dev.marzahn.gloriana.logic.characters.NoteRole
import dev.marzahn.gloriana.logic.characters.Pronouns
import dev.marzahn.gloriana.ui.files.SavedFileType
import dev.marzahn.gloriana.ui.files.proposedFileName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CharacterRulesTest {
  private val damaged = FileImportResult.Failed(ImportFailure.Damaged(DamagedAs.Png))

  @Test
  fun `every file left out says nothing`() {
    val leftOut = List(2) { FileImportResult.LeftOut }
    assertEquals(ImportOutcome.NothingImported, importOutcome(listOf("a", "b"), leftOut, timedOut = false))
  }

  @Test
  fun `one file imported opens its page`() {
    val id = CharacterIds[1]
    val imported = listOf(FileImportResult.Imported(id))
    assertEquals(ImportOutcome.OneImported(id), importOutcome(listOf("a"), imported, false))
    assertEquals(ImportOutcome.OneImported(id), importOutcome(listOf("a"), listOf(FileImportResult.Updated(id)), false))
  }

  @Test
  fun `several imported counts new characters, copies and updates but not files left out`() {
    val results = listOf(
      FileImportResult.Imported(CharacterIds[1]),
      FileImportResult.Updated(CharacterIds[2]),
      FileImportResult.LeftOut,
    )
    assertEquals(ImportOutcome.SeveralImported(2), importOutcome(listOf("a", "b", "c"), results, false))
  }

  @Test
  fun `any failure lists the failures instead of a count`() {
    val results = listOf(FileImportResult.Imported(CharacterIds[1]), damaged)
    val outcome = importOutcome(listOf("a", "broken.png"), results, false)
    val failure = FailedFile("broken.png", ImportFailure.Damaged(DamagedAs.Png))
    assertEquals(ImportOutcome.Failed(ImportNote.Failed(2, listOf(failure))), outcome)
  }

  @Test
  fun `time running out lists the files cut off and the failures together`() {
    val results = listOf(FileImportResult.Imported(CharacterIds[1]), damaged, null, null)
    val outcome = importOutcome(listOf("a", "b", "c", "d"), results, timedOut = true)
    val note = (outcome as ImportOutcome.TimedOut).note
    assertEquals(listOf("c", "d"), note.cutOff)
    assertEquals(3, note.notImported)
  }

  @Test
  fun `the page shows fields in the page order and hides empty ones`() {
    val fields = CharacterFields(
      name = "Tamsin",
      nickname = "Tam",
      description = "A sailor.",
      scenario = "",
      firstMessage = "",
      alternateGreetings = listOf("Hi", "", "Ahoy"),
      groupOnlyGreetings = listOf("All aboard"),
      exampleMessages = "<START>",
      systemPrompt = "Be brief.",
      postHistoryInstructions = "Stay in character.",
      characterNote = CharacterNote("Remember the sea.", 4, NoteRole.Assistant),
      creatorNotes = "Made for fun.",
    )
    val labels = sentToTheModel(character(fields)).map { it.label }
    assertEquals(
      listOf(
        FieldLabel.Nickname,
        FieldLabel.Description,
        FieldLabel.AlternateGreeting(1),
        FieldLabel.AlternateGreeting(3),
        FieldLabel.GroupGreeting(1),
        FieldLabel.ExampleMessages,
        FieldLabel.MainPrompt,
        FieldLabel.PostHistoryInstructions,
        FieldLabel.CharactersNote(4, NoteRole.Assistant),
      ),
      labels,
    )
    assertEquals(FieldLabel.CreatorNotes, creatorNotes(character(fields))?.label)
    assertNull(creatorNotes(character()))
  }

  @Test
  fun `pronouns list only the forms that are set`() {
    assertEquals(listOf("she", "her"), Pronouns(subject = "she", possessive = "her", reflexive = " ").forms())
    assertEquals(emptyList(), Pronouns.None.forms())
  }

  @Test
  fun `the proposed file name replaces characters files can't hold`() {
    assertEquals("Oren _ The Storm_.png", proposedFileName("Oren | The Storm?", SavedFileType.Png))
    assertEquals("a_b.json", proposedFileName("a/b", SavedFileType.Json))
    assertEquals("_.png", proposedFileName("  ", SavedFileType.Png))
  }

  @Test
  fun `lists join with the language's patterns`() {
    val two = " and "
    val more = ", "
    assertEquals("a", joinedList(listOf("a"), two, more))
    assertEquals("a and b", joinedList(listOf("a", "b"), two, more))
    assertEquals("a, b and c", joinedList(listOf("a", "b", "c"), two, more))
  }
}
