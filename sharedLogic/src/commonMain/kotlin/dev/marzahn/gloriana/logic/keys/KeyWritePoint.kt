package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.debug.FaultPoint

enum class KeyWritePoint : FaultPoint {
  BeforeDataKeyRemoved,
  NewKeysetStaged,
  ;

  override val key: String
    get() = "keys.$name"
}
