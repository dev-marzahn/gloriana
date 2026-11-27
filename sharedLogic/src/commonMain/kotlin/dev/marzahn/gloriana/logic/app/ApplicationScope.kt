package dev.marzahn.gloriana.logic.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

class ApplicationScope(
  dispatcher: CoroutineDispatcher = Dispatchers.IO,
  onFailure: (Throwable) -> Unit,
) : CoroutineScope {
  override val coroutineContext: CoroutineContext =
    SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error -> onFailure(error) }
}

expect fun stopApp(cause: Throwable)
