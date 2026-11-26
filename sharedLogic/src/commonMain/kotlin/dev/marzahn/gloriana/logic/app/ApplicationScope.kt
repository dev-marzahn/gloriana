package dev.marzahn.gloriana.logic.app

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

class ApplicationScope(onFailure: (Throwable) -> Unit) : CoroutineScope {
  override val coroutineContext: CoroutineContext =
    SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error -> onFailure(error) }
}

expect fun stopApp(cause: Throwable)
