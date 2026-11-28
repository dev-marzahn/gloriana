package dev.marzahn.gloriana.logic.settings

import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.uuid.Uuid

enum class MakeDefaultResult {
  Made,
  Gone,
  Failed,
}

internal class DefaultRow(
  private val database: GlorianaDatabase,
  private val settings: LibrarySettings,
  private val setting: LibrarySetting<Uuid>,
  private val storedIds: Flow<List<Uuid>>,
  private val exists: suspend (Uuid) -> Boolean,
) {
  fun observe(): Flow<Uuid?> =
    combine(settings.observe(setting), storedIds) { named, stored -> named?.takeIf { it in stored } }

  suspend fun current(): Uuid? = settings.read(setting)?.takeIf { exists(it) }

  suspend fun make(id: Uuid): MakeDefaultResult = database.writeTransaction {
    if (exists(id)) {
      name(id)
      MakeDefaultResult.Made
    } else {
      MakeDefaultResult.Gone
    }
  }

  suspend fun name(id: Uuid) = settings.write(setting, id)

  suspend fun clear() = settings.write(setting, null)
}
