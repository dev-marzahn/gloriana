package dev.marzahn.gloriana.logic.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationScopeTest {
  @Test
  fun aFailingLaunchReachesOnFailureAndItsSiblingsKeepRunning() = runBlocking {
    val failure = CompletableDeferred<Throwable>()
    val scope = ApplicationScope { failure.complete(it) }
    val siblingGoesOn = CompletableDeferred<Unit>()
    val sibling = scope.launch { siblingGoesOn.await() }

    scope.launch { error("the open failed") }
    val reported = withTimeout(TIMEOUT_MILLIS) { failure.await() }
    siblingGoesOn.complete(Unit)
    sibling.join()

    assertEquals("the open failed", reported.message)
    assertTrue(sibling.isCompleted && !sibling.isCancelled)
    scope.cancel()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
