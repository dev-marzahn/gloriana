package dev.marzahn.gloriana.logic.keys

import kotlin.uuid.Uuid

sealed interface FiledDataKeys {
  data object NotReadYet : FiledDataKeys

  data class Read(val connections: Set<Uuid>) : FiledDataKeys
}

class KeyFindings internal constructor(
  private val filed: FiledDataKeys,
  private val foundMissing: Set<SecretSlot>,
) {
  fun isMissing(slot: SecretSlot): Boolean {
    val noDataKey = when (filed) {
      FiledDataKeys.NotReadYet -> false
      is FiledDataKeys.Read -> slot.connectionId !in filed.connections
    }
    return noDataKey || slot in foundMissing
  }
}
