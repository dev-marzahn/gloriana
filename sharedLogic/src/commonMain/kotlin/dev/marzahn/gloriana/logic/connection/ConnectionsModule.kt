package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.connection.storage.ConnectionDao
import dev.marzahn.gloriana.logic.connection.storage.ConnectionWrites
import dev.marzahn.gloriana.logic.connection.storage.DefaultConnection
import dev.marzahn.gloriana.logic.connection.storage.needsDataKey
import dev.marzahn.gloriana.logic.keys.StoredConnections
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.uuid.Uuid

val connectionsModule = module {
  single { get<GlorianaDatabase>().connectionDao() }
  single { ConnectionsNeedingDataKeys(get()) } bind StoredConnections::class
  single { DefaultConnection(get(), get()) }
  single { ConnectionWrites(get(), get(), get(), get(), get(), get()) }
  single { ConnectionRepository(get(), get(), get(), get(), get(), get()) }
  single { GlobalModelSettings(get()) }
  single { KeysUnlock(get(), get()) }
}

internal class ConnectionsNeedingDataKeys(private val dao: ConnectionDao) : StoredConnections {
  override suspend fun ids(): Set<Uuid> = dao.allSecretColumns().filter { it.needsDataKey }.mapTo(HashSet()) { it.id }
}
