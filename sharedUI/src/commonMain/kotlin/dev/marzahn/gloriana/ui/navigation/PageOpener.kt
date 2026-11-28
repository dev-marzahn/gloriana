package dev.marzahn.gloriana.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

internal class PageOpener<P : Any>(private val missed: (P) -> Unit) {
  private data class Viewers<P : Any>(val count: Int, val pending: P?)

  private val viewers = MutableStateFlow(Viewers<P>(count = 0, pending = null))

  val shown: Boolean get() = viewers.value.count > 0

  fun offer(page: P): Boolean {
    val before = viewers.getAndUpdate { if (it.count > 0) it.copy(pending = page) else it }
    if (before.count == 0) return false
    before.pending?.takeIf { it != page }?.let(missed)
    return true
  }

  suspend fun openWhileShown(open: (P) -> Unit): Nothing {
    viewers.update { it.copy(count = it.count + 1) }
    try {
      viewers.collect { state -> state.pending?.let { page -> if (take(page)) open(page) } }
    } finally {
      val before = viewers.getAndUpdate { Viewers(it.count - 1, if (it.count > 1) it.pending else null) }
      if (before.count == 1) before.pending?.let(missed)
    }
  }

  private fun take(page: P): Boolean {
    val before = viewers.getAndUpdate { if (it.pending == page) it.copy(pending = null) else it }
    return before.pending == page
  }
}
