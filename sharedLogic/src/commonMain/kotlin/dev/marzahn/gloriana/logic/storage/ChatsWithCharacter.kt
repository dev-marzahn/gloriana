package dev.marzahn.gloriana.logic.storage

import kotlin.uuid.Uuid

interface ChatsWithCharacter {
  suspend fun count(characterId: Uuid): Int

  suspend fun delete(characterId: Uuid)

  suspend fun keepWithout(characterId: Uuid)
}
