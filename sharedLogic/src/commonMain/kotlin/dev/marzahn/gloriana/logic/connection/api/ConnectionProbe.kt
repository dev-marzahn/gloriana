package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionDraft
import dev.marzahn.gloriana.logic.connection.ConnectionProblem

sealed interface ProbeResult<out T> {
  data class Done<T>(val value: T) : ProbeResult<T>

  data class Refused(val problems: Set<ConnectionProblem>) : ProbeResult<Nothing>

  data class Failed(val error: ModelApiError) : ProbeResult<Nothing>
}

interface ConnectionProbe {
  suspend fun test(draft: ConnectionDraft, stored: Connection?): ProbeResult<String>

  suspend fun models(draft: ConnectionDraft, stored: Connection?): ProbeResult<List<String>>
}
