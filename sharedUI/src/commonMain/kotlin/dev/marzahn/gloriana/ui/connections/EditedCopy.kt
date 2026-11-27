package dev.marzahn.gloriana.ui.connections

interface ComparesByValue<T> {
  fun sameAs(other: T): Boolean
}

data class EditedCopy<T : ComparesByValue<T>>(val draft: T, val stored: T) {
  val hasChanges: Boolean get() = !draft.sameAs(stored)

  fun edited(change: (T) -> T) = copy(draft = change(draft))

  fun discarded() = copy(draft = stored)

  companion object {
    fun <T : ComparesByValue<T>> of(stored: T) = EditedCopy(stored, stored)
  }
}
