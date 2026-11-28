package dev.marzahn.gloriana.ui.state

class UnsavedSaves<S, T : Any>(
  private val cell: StateCell<S>,
  private val unsavedIn: (S) -> UnsavedCopy<T>?,
  private val withUnsaved: (S, UnsavedCopy<T>) -> S,
) {
  fun start(ready: (S, UnsavedCopy<T>) -> Boolean, started: (S) -> S = { it }): T? = cell.claim { current ->
    val unsaved = unsavedIn(current)?.takeIf { !it.isSaving && ready(current, it) } ?: return@claim null
    started(withUnsaved(current, unsaved.savingStarted())) to unsaved.draft
  }

  fun saved(written: T, then: (S) -> S = { it }) = finish(then) { it.saved(written) }

  fun notSaved(then: (S) -> S = { it }) = finish(then) { it.notSaved() }

  private fun finish(then: (S) -> S, change: (UnsavedCopy<T>) -> UnsavedCopy<T>) = cell.update { current ->
    then(unsavedIn(current)?.let { withUnsaved(current, change(it)) } ?: current)
  }
}
