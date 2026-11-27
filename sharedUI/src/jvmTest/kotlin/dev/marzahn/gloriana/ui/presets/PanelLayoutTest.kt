package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.EntryRole
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.PresetRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private const val ROW = 60

private fun entry(name: String) =
  PresetEntry(Uuid.random(), name, name, "", false, true, EntryRole.System, EntryPlacement.Relative)

class PanelLayoutTest {
  private val a = entry("a")
  private val b = entry("b")
  private val c = entry("c")
  private val first = PresetGroup(Uuid.random(), "first")
  private val second = PresetGroup(Uuid.random(), "second")

  private fun layout(rows: List<PresetRow>, hidden: Set<Uuid> = emptySet()) =
    PanelLayout(rows, hidden, rows.associate { it.id to ROW })

  @Test
  fun topsFollowTheOrderAndSkipHiddenRows() {
    val panel = layout(listOf(a, first, b, c), hidden = setOf(b.id))

    assertEquals(mapOf(a.id to 0f, first.id to 60f, c.id to 120f), panel.tops)
  }

  @Test
  fun anEntryMovesOnceItsLeadingEdgeIsAMarginPastTheNeighboursMiddle() {
    val panel = layout(listOf(a, b, c))

    assertNull(panel.step(a.id, draggedTop = 36f))
    assertEquals(MoveDirection.Down, panel.step(a.id, draggedTop = 37f))
    assertNull(panel.step(a.id, draggedTop = -10f))
  }

  @Test
  fun jitterAtTheMiddleAfterASwapDoesNotSwapBack() {
    val swapped = layout(listOf(b, a, c))

    listOf(33f, 37f, 30f, 40f).forEach { assertNull(swapped.step(a.id, draggedTop = it)) }
    assertEquals(MoveDirection.Up, swapped.step(a.id, draggedTop = 23f))
  }

  @Test
  fun aGroupWithEntriesStepsAgainstTheNextHeader() {
    val panel = layout(listOf(first, a, b, second, c), hidden = setOf(a.id, b.id))

    assertNull(panel.step(first.id, draggedTop = 20f))
    assertEquals(MoveDirection.Down, panel.step(first.id, draggedTop = 61f))
  }

  @Test
  fun anEntryAboveAFoldedGroupStepsPastItsHeader() {
    val panel = layout(listOf(a, first, b, c), hidden = setOf(b.id))

    assertEquals(MoveDirection.Down, panel.step(a.id, draggedTop = 37f))
  }
}
