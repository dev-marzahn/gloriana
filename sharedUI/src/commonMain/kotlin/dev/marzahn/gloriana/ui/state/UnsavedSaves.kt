package dev.marzahn.gloriana.ui.state

sealed interface SaveStart<out T, out R> {
  data class Started<T>(val draft: T) : SaveStart<T, Nothing>

  data class Refused<R>(val reason: R) : SaveStart<Nothing, R>
}

class UnsavedSaves<S, T : Any>(
  private val cell: StateCell<S>,
  private val unsavedIn: (S) -> UnsavedCopy<T>?,
  private val withUnsaved: (S, UnsavedCopy<T>) -> S,
) {
  fun start(ready: (S, UnsavedCopy<T>) -> Boolean, started: (S) -> S = { it }): T? = cell.claim { current ->
    val unsaved = unsavedIn(current)?.takeIf { ready(current, it) } ?: return@claim null
    savingStarted(current, unsaved, started) to unsaved.draft
  }

  fun <R : Any> startUnless(
    ready: (S, UnsavedCopy<T>) -> Boolean,
    refusal: (T) -> R?,
    refused: (S, R) -> S,
    started: (S) -> S = { it },
  ): SaveStart<T, R>? = cell.claim { current ->
    val unsaved = unsavedIn(current)?.takeIf { ready(current, it) } ?: return@claim null
    when (val reason = refusal(unsaved.draft)) {
      null -> savingStarted(current, unsaved, started) to SaveStart.Started(unsaved.draft)
      else -> refused(current, reason) to SaveStart.Refused(reason)
    }
  }

  private fun savingStarted(current: S, unsaved: UnsavedCopy<T>, started: (S) -> S): S =
    started(withUnsaved(current, unsaved.savingStarted()))

  fun saved(written: T, then: (S) -> S = { it }) = finish(then) { it.saved(written) }

  fun notSaved(then: (S) -> S = { it }) = finish(then) { it.notSaved() }

  private fun finish(then: (S) -> S, change: (UnsavedCopy<T>) -> UnsavedCopy<T>) = cell.update { current ->
    unsavedIn(current)?.takeIf { it.isSaving }?.let { then(withUnsaved(current, change(it))) } ?: current
  }
}
