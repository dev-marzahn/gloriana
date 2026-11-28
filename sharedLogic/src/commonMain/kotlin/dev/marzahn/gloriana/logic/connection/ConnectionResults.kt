package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.keys.KeysBlocked

sealed interface ConnectionSaveResult {
  class Saved(val connection: Connection) : ConnectionSaveResult

  data class Refused(val problems: Set<ConnectionProblem>) : ConnectionSaveResult

  data object NeedsPassphrase : ConnectionSaveResult

  data class Blocked(val keys: KeysBlocked) : ConnectionSaveResult

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
