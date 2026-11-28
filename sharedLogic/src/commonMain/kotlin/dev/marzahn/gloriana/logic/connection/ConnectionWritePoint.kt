package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.debug.FaultPoint

enum class ConnectionWritePoint : FaultPoint {
  InsertRowWritten,
  UpdateRowWritten,
  DeleteRowRemoved,
  MoveRowsWritten,
  ;

  override val key: String
    get() = "connections.$name"
}
