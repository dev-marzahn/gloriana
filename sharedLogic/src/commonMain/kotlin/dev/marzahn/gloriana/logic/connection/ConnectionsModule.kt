package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.connection.storage.ConnectionDao
import dev.marzahn.gloriana.logic.connection.storage.ConnectionWrites
import dev.marzahn.gloriana.logic.connection.storage.DefaultConnection
import dev.marzahn.gloriana.logic.keys.StoredConnections
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.uuid.Uuid

val connectionsModule = module {
  single { get<GlorianaDatabase>().connectionDao() }
  single { StoredConnectionIds(get()) } bind StoredConnections::class
  single { ConnectionChats.NoneYet }
  single { DefaultConnection(get(), get()) }
  single { ConnectionWrites(get(), get(), get(), get(), get()) }
  single { ConnectionRepository(get(), get(), get(), get(), get(), get()) }
  single { GlobalModelSettings(get()) }
}

internal class StoredConnectionIds(private val dao: ConnectionDao) : StoredConnections {
  override suspend fun ids(): Set<Uuid> = dao.ids().toSet()
}
