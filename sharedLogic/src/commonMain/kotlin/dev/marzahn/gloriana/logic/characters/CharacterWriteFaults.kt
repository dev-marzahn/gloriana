package dev.marzahn.gloriana.logic.characters

enum class CharacterWritePoint {
  InsertRowWritten,
  UpdateChildrenRemoved,
  UpdateRowWritten,
  DeleteChildrenRemoved,
  DeleteRowRemoved,
}

fun interface CharacterWriteFaults {
  fun reached(point: CharacterWritePoint)

  companion object {
    val None = CharacterWriteFaults { }
  }
}
