package dev.marzahn.gloriana.logic.storage

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun interface IdFactory {
  fun newId(): Uuid
}

object UuidV7IdFactory : IdFactory {
  @OptIn(ExperimentalUuidApi::class)
  override fun newId(): Uuid = Uuid.generateV7()
}
