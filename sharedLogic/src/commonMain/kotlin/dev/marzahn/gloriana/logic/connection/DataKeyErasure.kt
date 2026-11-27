package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.connection.storage.ConnectionDao
import dev.marzahn.gloriana.logic.connection.storage.needsDataKey
import dev.marzahn.gloriana.logic.keys.KeyFileResult
import dev.marzahn.gloriana.logic.keys.KeyVault
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

private const val ERASE_TRIES = 2

internal class DataKeyErasure(private val vault: KeyVault, private val dao: ConnectionDao) {
  private val writesAndErases = Mutex()

  suspend fun <T> oneAtATime(work: suspend Erasing.() -> T): T = writesAndErases.withLock { Erasing().work() }

  inner class Erasing internal constructor() {
    suspend fun eraseUnlessStoredValuesNeedIt(id: Uuid) {
      if (!failingAs(true) { storedValuesNeedKey(id) }) erase(id)
    }

    suspend fun erase(id: Uuid): Boolean = (1..ERASE_TRIES).any {
      failingAs(KeyFileResult.Unavailable) { vault.erase(id) } == KeyFileResult.Done
    }
  }

  private suspend fun storedValuesNeedKey(id: Uuid): Boolean = dao.find(id)?.needsDataKey ?: false
}
