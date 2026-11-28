package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.json.asString
import dev.marzahn.gloriana.logic.json.parsedObject
import dev.marzahn.gloriana.logic.storage.createDirectoriesDurably
import dev.marzahn.gloriana.logic.storage.writeAtomically
import dev.marzahn.gloriana.logic.text.parsedOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.uuid.Uuid

internal class DataKeyFile(private val path: Path) {
  fun read(): Map<Uuid, ByteArray> =
    filed()?.entries?.mapNotNull { (id, key) -> entry(id, key) }?.toMap().orEmpty()

  private fun filed(): JsonObject? =
    if (Files.isRegularFile(path)) parsedObject(Files.readAllBytes(path).decodeToString()) else null

  fun write(keys: Map<Uuid, ByteArray>) {
    val filed = JsonObject(
      keys.entries.associate { (id, key) -> id.toString() to JsonPrimitive(Base64.getEncoder().encodeToString(key)) },
    )
    path.parent.createDirectoriesDurably()
    path.writeAtomically { it.write(filed.toString().encodeToByteArray()) }
  }

  private fun entry(id: String, key: JsonElement): Pair<Uuid, ByteArray>? {
    val connection = Uuid.parseOrNull(id)
    val bytes = key.asString()?.let { encoded -> parsedOrNull { Base64.getDecoder().decode(encoded) } }
    return if (connection == null || bytes == null) null else connection to bytes
  }
}
