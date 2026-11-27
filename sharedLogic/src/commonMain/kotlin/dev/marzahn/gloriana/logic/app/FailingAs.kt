package dev.marzahn.gloriana.logic.app

import kotlinx.coroutines.CancellationException

suspend fun <T> failingAs(failed: T, work: suspend () -> T): T = failingAs({ failed }, work)

@Suppress("TooGenericExceptionCaught")
suspend fun <T> failingAs(failed: (Exception) -> T, work: suspend () -> T): T = try {
  work()
} catch (cancelled: CancellationException) {
  throw cancelled
} catch (error: Exception) {
  failed(error)
}
