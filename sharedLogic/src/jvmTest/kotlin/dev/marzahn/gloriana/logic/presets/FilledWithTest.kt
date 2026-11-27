package dev.marzahn.gloriana.logic.presets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class FilledWithTest {
  private fun entry(identifier: String, filledByApp: Boolean) =
    PresetEntry(Uuid.random(), identifier, identifier, "", filledByApp, true, EntryRole.System, EntryPlacement.Relative)

  @Test
  fun anEntryTheAppFillsInNamesWhatItIsFilledWith() {
    assertEquals(FilledWith.ChatMessages, entry("chatHistory", filledByApp = true).filledWith)
    assertEquals(FilledWith.LorebookEntries, entry("worldInfoAfter", filledByApp = true).filledWith)
  }

  @Test
  fun anEntryWithItsOwnTextOrAnUnknownMarkerHasNoKind() {
    assertNull(entry("chatHistory", filledByApp = false).filledWith)
    assertNull(entry("somethingNew", filledByApp = true).filledWith)
  }
}
