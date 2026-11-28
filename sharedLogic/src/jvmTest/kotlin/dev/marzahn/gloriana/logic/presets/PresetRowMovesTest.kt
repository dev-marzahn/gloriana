package dev.marzahn.gloriana.logic.presets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private fun entry(name: String) = PresetEntry(
  id = Uuid.random(),
  identifier = name,
  name = name,
  text = "",
  filledByApp = false,
  enabled = true,
  role = EntryRole.System,
  placement = EntryPlacement.Relative,
)

private fun group(name: String) = PresetGroup(Uuid.random(), name)

private fun List<PresetRow>.names() = map {
  when (it) {
    is PresetEntry -> it.name
    is PresetGroup -> "[${it.name}]"
  }
}

class PresetRowMovesTest {
  private val a = entry("a")
  private val b = entry("b")
  private val c = entry("c")
  private val d = entry("d")
  private val first = group("first")
  private val second = group("second")

  @Test
  fun anEntryMovesOnePlaceAndPassingAHeaderChangesItsGroup() {
    val rows = listOf(a, first, b)

    val moved = rows.moved(a.id, MoveDirection.Down)

    assertEquals(listOf("[first]", "a", "b"), moved.names())
    assertEquals(first, moved.groupOf(a.id))
    assertFalse(rows.canMove(a.id, MoveDirection.Up))
  }

  @Test
  fun aGroupMovesPastTheWholeNeighbouringGroupWithItsEntries() {
    val rows = listOf(first, a, b, second, c)

    val down = rows.moved(first.id, MoveDirection.Down)
    val up = down.moved(first.id, MoveDirection.Up)

    assertEquals(listOf("[second]", "c", "[first]", "a", "b"), down.names())
    assertEquals(rows, up)
  }

  @Test
  fun theFirstHeaderCantMoveUpAndTheLastCantMoveDown() {
    val rows = listOf(a, first, b, second, c)

    assertFalse(rows.canMove(first.id, MoveDirection.Up))
    assertFalse(rows.canMove(second.id, MoveDirection.Down))
    assertTrue(rows.canMove(first.id, MoveDirection.Down))
  }

  @Test
  fun aHeaderWithoutEntriesMovesOneRowLikeAnEntry() {
    val rows = listOf(a, b, first)

    val moved = rows.moved(first.id, MoveDirection.Up)

    assertEquals(listOf("a", "[first]", "b"), moved.names())
    assertEquals(first, moved.groupOf(b.id))
  }

  @Test
  fun addingRenamingAndDeletingAGroupNeverReordersTheEntries() {
    val rows = listOf(a, b, c, d)

    val added = rows.withGroupAdded(first, above = c.id)
    val renamed = added.withGroupRenamed(first.id, "renamed")
    val deleted = renamed.withoutGroup(first.id)

    assertEquals(listOf("a", "b", "[first]", "c", "d"), added.names())
    assertEquals(listOf(c, d), added.entriesUnder(first.id))
    assertEquals("renamed", (renamed[2] as PresetGroup).name)
    assertEquals(rows, deleted)
    assertEquals(rows.filterIsInstance<PresetEntry>(), added.filterIsInstance<PresetEntry>())
  }

  @Test
  fun aGroupAddedAboveNothingGoesAtTheEnd() {
    assertEquals(listOf("a", "[first]"), listOf<PresetRow>(a).withGroupAdded(first, above = null).names())
  }

  @Test
  fun aPlaceCountsEntriesAndGroupsSeparately() {
    val rows = listOf(a, first, b, second, c)

    val entryPlace = rows.placeOf(b.id)
    val groupPlace = rows.placeOf(second.id)

    assertEquals(2, entryPlace?.position)
    assertEquals(3, entryPlace?.of)
    assertEquals(first, entryPlace?.group)
    assertEquals(2, groupPlace?.position)
    assertEquals(2, groupPlace?.of)
  }
}
