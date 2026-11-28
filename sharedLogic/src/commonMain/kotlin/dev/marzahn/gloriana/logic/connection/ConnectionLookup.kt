package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.connection.storage.ConnectionDao
import kotlin.uuid.Uuid

class ConnectionLookup internal constructor(private val dao: ConnectionDao) {
  suspend fun exists(id: Uuid): Boolean = dao.exists(id)
}
