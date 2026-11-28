package dev.marzahn.gloriana.logic.connection.storage

import dev.marzahn.gloriana.logic.settings.DefaultRow
import dev.marzahn.gloriana.logic.settings.LibrarySettings
import dev.marzahn.gloriana.logic.settings.MakeDefaultResult
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

internal class DefaultConnection(database: GlorianaDatabase, settings: LibrarySettings, dao: ConnectionDao) {
  private val row = DefaultRow(database, settings, DefaultConnectionSetting, dao.observeIds()) { dao.find(it) != null }

  fun observe(): Flow<Uuid?> = row.observe()

  suspend fun make(id: Uuid): MakeDefaultResult = row.make(id)

  suspend fun claimIfUnset(id: Uuid) {
    if (row.current() == null) row.name(id)
  }

  suspend fun clearIf(id: Uuid) {
    if (row.current() == id) row.clear()
  }
}
