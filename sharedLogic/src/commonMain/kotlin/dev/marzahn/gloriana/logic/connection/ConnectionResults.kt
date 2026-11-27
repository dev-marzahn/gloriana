package dev.marzahn.gloriana.logic.connection

import kotlin.uuid.Uuid

sealed interface ConnectionSaveResult {
  data class Saved(val id: Uuid) : ConnectionSaveResult

  data class Refused(val problems: Set<ConnectionProblem>) : ConnectionSaveResult

  data object Locked : ConnectionSaveResult

  data object Gone : ConnectionSaveResult

  data object Unreadable : ConnectionSaveResult

  data object StorageFull : ConnectionSaveResult

  data object Failed : ConnectionSaveResult
}

enum class ConnectionDeleteResult {
  Deleted,
  DeletedKeyLeftForNextStart,
  Gone,
  Failed,
}

enum class MakeDefaultResult {
  Made,
  Gone,
  Failed,
}
