package dev.marzahn.gloriana.logic.keys

import kotlin.uuid.Uuid

fun interface StoredConnections {
  suspend fun ids(): Set<Uuid>
}
