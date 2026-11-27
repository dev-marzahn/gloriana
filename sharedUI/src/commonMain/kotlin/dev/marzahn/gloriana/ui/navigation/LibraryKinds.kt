package dev.marzahn.gloriana.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LibraryKind { Characters, Presets }

class LibraryKinds {
  private val requested = MutableStateFlow<LibraryKind?>(null)

  val request: StateFlow<LibraryKind?> = requested.asStateFlow()

  fun show(kind: LibraryKind) {
    requested.value = kind
  }

  fun handled(kind: LibraryKind) {
    requested.compareAndSet(kind, null)
  }
}

fun ShellNavigator.showLibraryKind(kind: LibraryKind, kinds: LibraryKinds, then: () -> Unit = {}) {
  if (current != Destination.Library) select(Destination.Library)
  returnToRoot(Destination.Library) {
    kinds.show(kind)
    then()
  }
}
