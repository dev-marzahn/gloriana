package dev.marzahn.gloriana.logic.connection.storage

import dev.marzahn.gloriana.logic.settings.LibrarySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.uuid.Uuid

internal class DefaultConnection(private val settings: LibrarySettings, private val dao: ConnectionDao) {
  fun observe(): Flow<Uuid?> =
    combine(settings.observe(DefaultConnectionSetting), dao.observeIds()) { named, stored ->
      named.unlessGone { it in stored }
    }

  suspend fun claimIfUnset(id: Uuid) {
    if (settings.read(DefaultConnectionSetting).unlessGone { dao.find(it) != null } == null) set(id)
  }

  suspend fun set(id: Uuid) = settings.write(DefaultConnectionSetting, id)

  suspend fun clearIf(id: Uuid) {
    if (settings.read(DefaultConnectionSetting) == id) settings.write(DefaultConnectionSetting, null)
  }
}

private inline fun Uuid?.unlessGone(exists: (Uuid) -> Boolean): Uuid? = this?.takeIf(exists)
