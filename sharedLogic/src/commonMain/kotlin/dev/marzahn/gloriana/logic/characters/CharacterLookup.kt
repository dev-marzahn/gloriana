package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.characters.storage.CharacterDao
import dev.marzahn.gloriana.logic.characters.storage.toCharacter
import kotlin.uuid.Uuid

class CharacterLookup internal constructor(private val dao: CharacterDao) {
  suspend fun character(id: Uuid): Character? = dao.rows(id)?.toCharacter()
}
