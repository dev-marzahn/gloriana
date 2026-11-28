package dev.marzahn.gloriana.ui.navigation

import dev.marzahn.gloriana.logic.storage.IdFactory
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class Opening(val id: Uuid) {
  companion object {
    fun fresh(ids: IdFactory) = Opening(ids.newId())
  }
}
