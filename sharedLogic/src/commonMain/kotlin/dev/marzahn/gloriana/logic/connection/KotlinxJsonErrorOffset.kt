package dev.marzahn.gloriana.logic.connection

import kotlinx.serialization.SerializationException

internal object KotlinxJsonErrorOffset {
  private val offsetInMessage = Regex("at offset (\\d+)")

  fun of(error: SerializationException): Int? =
    error.message?.let(offsetInMessage::find)?.groupValues?.get(1)?.toIntOrNull()
}
