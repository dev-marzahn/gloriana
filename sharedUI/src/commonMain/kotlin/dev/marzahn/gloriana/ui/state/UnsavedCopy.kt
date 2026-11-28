package dev.marzahn.gloriana.ui.state

interface DraftEdit<T> {
  fun applied(to: T): T

  fun replaces(earlier: DraftEdit<T>): Boolean = false
}

fun interface SameValue<T> {
  fun same(one: T, other: T): Boolean
}

private class RunningSave<T>(val edits: Int, val storedAtStart: T)

class UnsavedCopy<T : Any> private constructor(
  val stored: T,
  private val edits: List<DraftEdit<T>>,
  private val savedAhead: T?,
  private val running: RunningSave<T>?,
  private val sameValue: SameValue<T>,
) {
  val baseline: T get() = savedAhead ?: stored

  val draft: T = edits.fold(baseline) { value, edit -> edit.applied(value) }

  val hasChanges: Boolean get() = !sameValue.same(draft, baseline)

  val isSaving: Boolean get() = running != null

  val beingSaved: T?
    get() = running?.let { save -> edits.take(save.edits).fold(baseline) { value, edit -> edit.applied(value) } }

  val editsSinceSaveStarted: List<DraftEdit<T>> get() = edits.drop(running?.edits ?: 0)

  fun edited(edit: DraftEdit<T>): UnsavedCopy<T> {
    val last = edits.lastOrNull()
    val collapses = last != null && edits.size > (running?.edits ?: 0) && edit.replaces(last)
    return copy(edits = if (collapses) edits.dropLast(1) + edit else edits + edit)
  }

  val holdsNothingBeyondTheStoredValue: Boolean get() = edits.isEmpty() && running == null && savedAhead == null

  fun withEdits(more: List<DraftEdit<T>>): UnsavedCopy<T> =
    more.fold(this) { following, edit -> following.edited(edit) }

  fun discarded(): UnsavedCopy<T> =
    copy(edits = emptyList(), running = running?.let { RunningSave(edits = 0, it.storedAtStart) })

  fun storedNow(emission: T): UnsavedCopy<T> =
    if (emission == stored) this else copy(stored = emission, savedAhead = null)

  fun savingStarted(): UnsavedCopy<T> = copy(running = RunningSave(edits.size, stored))

  fun saved(written: T): UnsavedCopy<T> {
    val save = running ?: return this
    val noNewerRow = stored == save.storedAtStart && stored != written
    return copy(edits = edits.drop(save.edits), savedAhead = written.takeIf { noNewerRow }, running = null)
  }

  fun notSaved(): UnsavedCopy<T> = copy(running = null)

  private fun copy(
    stored: T = this.stored,
    edits: List<DraftEdit<T>> = this.edits,
    savedAhead: T? = this.savedAhead,
    running: RunningSave<T>? = this.running,
  ) = UnsavedCopy(stored, edits, savedAhead, running, sameValue)

  companion object {
    fun <T : Any> of(stored: T, sameValue: SameValue<T> = SameValue { one, other -> one == other }): UnsavedCopy<T> =
      UnsavedCopy(stored, emptyList(), savedAhead = null, running = null, sameValue)
  }
}
