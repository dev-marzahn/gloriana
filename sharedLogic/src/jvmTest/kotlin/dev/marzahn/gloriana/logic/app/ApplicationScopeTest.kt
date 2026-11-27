package dev.marzahn.gloriana.logic.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationScopeTest {
  @Test
  fun aFailingLaunchReachesOnFailureAndItsSiblingsKeepRunning() = runTest {
    val failure = CompletableDeferred<Throwable>()
    val scope = ApplicationScope(StandardTestDispatcher(testScheduler)) { failure.complete(it) }
    val siblingGoesOn = CompletableDeferred<Unit>()
    val sibling = scope.launch { siblingGoesOn.await() }

    scope.launch { error("the open failed") }
    val reported = failure.await()
    siblingGoesOn.complete(Unit)
    sibling.join()

    assertEquals("the open failed", reported.message)
    assertTrue(sibling.isCompleted && !sibling.isCancelled)
    scope.cancel()
  }
}
