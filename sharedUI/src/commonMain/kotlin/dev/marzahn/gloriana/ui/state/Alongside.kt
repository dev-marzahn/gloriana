package dev.marzahn.gloriana.ui.state

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

fun <T> Flow<T>.alongside(work: Flow<*>): Flow<T> = flow {
  coroutineScope {
    val working = launch { work.collect {} }
    try {
      emitAll(this@alongside)
    } finally {
      working.cancel()
    }
  }
}
