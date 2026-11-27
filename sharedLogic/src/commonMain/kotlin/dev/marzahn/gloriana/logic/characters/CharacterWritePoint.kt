package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.debug.FaultPoint

enum class CharacterWritePoint : FaultPoint {
  InsertRowWritten,
  UpdateChildrenRemoved,
  UpdateRowWritten,
  DeleteChildrenRemoved,
  DeleteRowRemoved,
  ;

  override val key: String
    get() = "characters.$name"
}
