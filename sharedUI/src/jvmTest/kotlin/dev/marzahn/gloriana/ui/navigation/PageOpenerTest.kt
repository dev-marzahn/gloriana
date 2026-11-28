package dev.marzahn.gloriana.ui.navigation

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PageOpenerTest {
  private val missed = mutableListOf<String>()
  private val opened = mutableListOf<String>()
  private val pages = PageOpener<String> { missed += it }

  @Test
  fun aPageOfferedWithNoListShownIsRefused() {
    assertFalse(pages.offer("a"))
    assertEquals(emptyList(), missed)
  }

  @Test
  fun aShownListOpensTheOfferedPage() = runTest {
    val list = launch(start = CoroutineStart.UNDISPATCHED) { pages.openWhileShown { opened += it } }
    assertTrue(pages.shown)
    assertTrue(pages.offer("a"))
    testScheduler.runCurrent()
    assertEquals(listOf("a"), opened)
    list.cancelAndJoin()
    assertFalse(pages.shown)
    assertEquals(emptyList(), missed)
  }

  @Test
  fun aPageTheListLeavesBeforeOpeningIsMissedNotKept() = runTest {
    val list = launch(start = CoroutineStart.UNDISPATCHED) { pages.openWhileShown { opened += it } }
    assertTrue(pages.offer("a"))
    list.cancelAndJoin()
    assertEquals(listOf("a"), missed)
    assertEquals(emptyList(), opened)
    val next = launch(start = CoroutineStart.UNDISPATCHED) { pages.openWhileShown { opened += it } }
    testScheduler.runCurrent()
    assertEquals(emptyList(), opened)
    next.cancelAndJoin()
  }

  @Test
  fun theSamePageOfferedTwiceOpensOnceAndIsNotMissed() = runTest {
    val list = launch(start = CoroutineStart.UNDISPATCHED) { pages.openWhileShown { opened += it } }
    pages.offer("a")
    pages.offer("a")
    testScheduler.runCurrent()
    assertEquals(listOf("a"), opened)
    assertEquals(emptyList(), missed)
    list.cancelAndJoin()
  }

  @Test
  fun aNewerOfferMissesThePageItReplaces() = runTest {
    val list = launch(start = CoroutineStart.UNDISPATCHED) { pages.openWhileShown { opened += it } }
    pages.offer("a")
    pages.offer("b")
    testScheduler.runCurrent()
    assertEquals(listOf("b"), opened)
    assertEquals(listOf("a"), missed)
    list.cancelAndJoin()
  }
}
