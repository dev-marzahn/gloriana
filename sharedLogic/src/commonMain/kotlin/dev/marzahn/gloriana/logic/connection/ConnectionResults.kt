package dev.marzahn.gloriana.logic.connection

import kotlin.uuid.Uuid

sealed interface ConnectionSaveResult {
  data class Saved(val id: Uuid) : ConnectionSaveResult

  data class Refused(val problems: Set<ConnectionProblem>) : ConnectionSaveResult

  data object NeedsPassphrase : ConnectionSaveResult

  data object Locked : ConnectionSaveResult

  data object KeysUnavailable : ConnectionSaveResult

  data object KeysUnreadable : ConnectionSaveResult

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

sealed interface ConnectionRead {
  class Readable(val connection: Connection) : ConnectionRead

  data object Unreadable : ConnectionRead

  data object Gone : ConnectionRead
}

sealed interface SamplersUpdateResult {
  data object Updated : SamplersUpdateResult

  data object Unchanged : SamplersUpdateResult

  data class Refused(val problems: Set<ConnectionProblem>) : SamplersUpdateResult

  data object Gone : SamplersUpdateResult

  data object Unreadable : SamplersUpdateResult

  data object StorageFull : SamplersUpdateResult

  data object Failed : SamplersUpdateResult
}

enum class MakeDefaultResult {
  Made,
  Gone,
  Failed,
}
