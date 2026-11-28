package dev.marzahn.gloriana.logic.settings

import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

internal class AlwaysSetDefault(private val database: GlorianaDatabase, private val row: DefaultRow) {
  fun observe(): Flow<Uuid?> = row.observe()

  suspend fun make(id: Uuid): MakeDefaultResult = row.make(id)

  suspend fun ensure(create: suspend () -> Uuid) {
    database.writeTransaction { if (row.current() == null) row.name(create()) }
  }

  suspend fun <T> unlessDefault(id: Uuid, refused: T, write: suspend (default: Uuid?) -> T): T =
    database.writeTransaction {
      when (val default = row.current()) {
        id -> refused
        else -> write(default)
      }
    }
}
