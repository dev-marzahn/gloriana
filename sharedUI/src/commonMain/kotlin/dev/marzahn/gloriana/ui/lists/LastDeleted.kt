package dev.marzahn.gloriana.ui.lists

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

data class Deleted(val id: Uuid, val index: Int)

class LastDeleted {
  private val deleted = MutableStateFlow<Deleted?>(null)

  val latest: StateFlow<Deleted?> = deleted.asStateFlow()

  fun record(delete: Deleted) {
    deleted.value = delete
  }

  fun handled(delete: Deleted) {
    deleted.compareAndSet(delete, null)
  }
}
