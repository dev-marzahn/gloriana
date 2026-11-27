package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.debug.FaultPoint

enum class PresetWritePoint : FaultPoint {
  InsertRowWritten,
  SaveRowWritten,
  DeleteChildrenRemoved,
  ;

  override val key: String
    get() = "presets.$name"
}
