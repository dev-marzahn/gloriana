package dev.marzahn.gloriana.ui.lists

import dev.marzahn.gloriana.logic.storage.MoveResult
import dev.marzahn.gloriana.logic.storage.Place
import dev.marzahn.gloriana.logic.storage.Reordering
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private infix fun Uuid.goes(to: Place): Pair<Uuid, Place> = this to to

@OptIn(ExperimentalCoroutinesApi::class)
class UserOrderMovesTest {
  private val a = Uuid.random()
  private val b = Uuid.random()
  private val c = Uuid.random()
  private val stored = MutableStateFlow(listOf(a, b, c))
  private val reordering = HeldReordering()

  private class HeldReordering : Reordering {
    val asked = mutableListOf<Pair<Uuid, Place>>()
    val answers = ArrayDeque<CompletableDeferred<MoveResult>>()

    override suspend fun move(id: Uuid, to: Place): MoveResult {
      asked += id to to
      return CompletableDeferred<MoveResult>().also(answers::addLast).await()
    }

    fun answer(result: MoveResult) = answers.removeFirst().complete(result)
  }

  private fun CoroutineScope.moves() = UserOrderMoves(this, reordering, stored) { it }

  @Test
  fun theMovedOrderShowsAtOnceAndEachMoveNamesTheRowItGoesBefore() = runTest {
    val moves = backgroundScope.moves()

    moves.move(listOf(b, a, c), b, "B")
    moves.move(listOf(b, c, a), c, "C")
    runCurrent()

    assertEquals(listOf(b, c, a), moves.arranged.first())
    assertEquals(listOf(b goes Place.Before(a)), reordering.asked)
    reordering.answer(MoveResult.Moved)
    runCurrent()
    assertEquals(listOf(b goes Place.Before(a), c goes Place.Before(a)), reordering.asked)
    assertEquals("B", moves.saved.value?.name)
  }

  @Test
  fun onceEveryMoveIsSavedTheNextStoredOrderShowsEvenFromAnotherWriter() = runTest {
    val moves = backgroundScope.moves()
    val arranged = moves.arranged.stateIn(backgroundScope, SharingStarted.Eagerly, emptyList())

    moves.move(listOf(b, a, c), b, "B")
    runCurrent()
    reordering.answer(MoveResult.Moved)
    runCurrent()
    assertEquals(listOf(b, a, c), arranged.value)

    stored.value = listOf(c, a, b)
    runCurrent()
    assertEquals(listOf(c, a, b), arranged.value)
  }

  @Test
  fun theSavedOrderEndsTheOverlayWhetherTheListIsShownOnceTwiceOrNotAtAll() = runTest {
    val moves = backgroundScope.moves()

    moves.move(listOf(b, a, c), b, "B")
    runCurrent()
    reordering.answer(MoveResult.Moved)
    runCurrent()
    stored.value = listOf(c, a, b)
    runCurrent()

    val first = moves.arranged.stateIn(backgroundScope, SharingStarted.Eagerly, emptyList())
    val second = moves.arranged.stateIn(backgroundScope, SharingStarted.Eagerly, emptyList())
    runCurrent()
    assertEquals(listOf(c, a, b), first.value)
    assertEquals(listOf(c, a, b), second.value)
  }

  @Test
  fun aMoveToTheEndGoesLast() = runTest {
    val moves = backgroundScope.moves()

    moves.move(listOf(b, c, a), a, "A")
    runCurrent()

    assertEquals(listOf(a goes Place.Last), reordering.asked)
  }

  @Test
  fun aFailedMoveDropsTheWaitingOnesAndShowsTheStoredOrder() = runTest {
    val moves = backgroundScope.moves()

    moves.move(listOf(b, a, c), b, "B")
    moves.move(listOf(b, c, a), c, "C")
    runCurrent()
    reordering.answer(MoveResult.StorageFull)
    runCurrent()

    assertEquals(listOf(a, b, c), moves.arranged.first())
    assertEquals(NotMoved("B", storageFull = true), moves.notMoved.value)
    assertEquals(1, reordering.asked.size)
  }

  @Test
  fun theNextMoveClearsTheNote() = runTest {
    val moves = backgroundScope.moves()

    moves.move(listOf(b, a, c), b, "B")
    runCurrent()
    reordering.answer(MoveResult.Failed)
    runCurrent()
    moves.move(listOf(b, a, c), b, "B")

    assertNull(moves.notMoved.value)
  }
}
