package dev.marzahn.gloriana.logic.characters.storage

import dev.marzahn.gloriana.logic.characters.Character
import dev.marzahn.gloriana.logic.characters.CharacterFields
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.characters.CharacterWritePoint
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlin.uuid.Uuid

internal sealed interface CharacterWrite {
  val id: Uuid

  data class Inserted(override val id: Uuid) : CharacterWrite

  data class Replaced(override val id: Uuid) : CharacterWrite
}

internal class CharacterWrites(
  private val database: GlorianaDatabase,
  private val ids: IdFactory,
  private val clock: UpdatedAtClock,
  private val tombstones: Tombstones,
  private val faults: Faults,
) {
  private val characters = database.characterDao()
  private val children = database.characterChildDao()

  suspend fun insert(content: CharacterContent): Uuid = database.writeTransaction {
    val id = ids.newId()
    val now = clock.now()
    val importedAt = maxOf(now, (characters.latestImportedAt() ?: Long.MIN_VALUE) + 1)
    characters.insert(content.toEntity(RowStamp(id, importedAt, updatedAt = now)))
    faults.reached(CharacterWritePoint.InsertRowWritten)
    insertChildren(content, id, now)
    id
  }

  suspend fun replaceMatchOrInsert(
    fields: CharacterFields,
    replacement: (previous: Character) -> CharacterContent,
    fresh: () -> CharacterContent,
  ): CharacterWrite = database.writeTransaction {
    val match = characters.lastImportedMatch(matchKey(fields.name), matchKey(fields.creator))
    val previous = match?.let { characters.rows(it.id) }
    if (previous == null) CharacterWrite.Inserted(insert(fresh())) else replace(previous, replacement)
  }

  suspend fun delete(id: Uuid): Boolean = database.writeTransaction {
    val previous = characters.rows(id)
    if (previous != null) {
      removeChildren(previous)
      faults.reached(CharacterWritePoint.DeleteChildrenRemoved)
      characters.delete(id)
      faults.reached(CharacterWritePoint.DeleteRowRemoved)
      tombstones.record(CharactersEntityType, RowKey.Row(id))
    }
    previous != null
  }

  private suspend fun replace(previous: CharacterRows, replacement: (Character) -> CharacterContent): CharacterWrite {
    val id = previous.character.id
    val now = clock.now()
    val stamp = RowStamp(id, previous.character.importedAt, clock.updatedAt(previous.character.updatedAt, now))
    val content = replacement(previous.toCharacter())
    removeChildren(previous)
    faults.reached(CharacterWritePoint.UpdateChildrenRemoved)
    characters.update(content.toEntity(stamp))
    faults.reached(CharacterWritePoint.UpdateRowWritten)
    insertChildren(content, id, now)
    return CharacterWrite.Replaced(id)
  }

  private suspend fun insertChildren(content: CharacterContent, id: Uuid, now: Long) {
    children.insertGreetings(content.greetingRows(id, updatedAt = now, ids::newId))
    children.insertAssets(content.assetRows(id, updatedAt = now, ids::newId))
  }

  private suspend fun removeChildren(previous: CharacterRows) {
    children.deleteGreetings(previous.character.id)
    children.deleteAssets(previous.character.id)
    previous.greetings.forEach { tombstones.record(CharacterGreetingsEntityType, RowKey.Row(it.id)) }
    previous.assets.forEach { tombstones.record(CharacterAssetsEntityType, RowKey.Row(it.id)) }
  }
}
