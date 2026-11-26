package dev.marzahn.gloriana.ui.library

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface LibraryState {
  data object Empty : LibraryState
}

class LibraryViewModel : ViewModel() {
  val state: StateFlow<LibraryState> = MutableStateFlow(LibraryState.Empty)
}
