package dev.marzahn.gloriana.logic.storage

import kotlin.uuid.Uuid

sealed interface RowKey {
  data class Row(val id: Uuid) : RowKey

  data class JoinRow(val first: Uuid, val second: Uuid) : RowKey
}
