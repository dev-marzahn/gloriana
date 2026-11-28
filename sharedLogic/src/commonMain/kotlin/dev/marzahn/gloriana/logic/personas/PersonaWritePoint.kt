package dev.marzahn.gloriana.logic.personas

import dev.marzahn.gloriana.logic.debug.FaultPoint

enum class PersonaWritePoint : FaultPoint {
  InsertRowWritten,
  UpdateRowWritten,
  DeleteRowRemoved,
  ;

  override val key: String
    get() = "personas.$name"
}
