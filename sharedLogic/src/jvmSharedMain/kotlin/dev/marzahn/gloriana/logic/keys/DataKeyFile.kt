package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.storage.createDirectoriesDurably
import dev.marzahn.gloriana.logic.storage.writeAtomically
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.uuid.Uuid

internal class DataKeyFile(private val path: Path) {
  fun read(): Map<Uuid, ByteArray> =
    filed()?.entries?.mapNotNull { (id, key) -> entry(id, key) }?.toMap().orEmpty()

  private fun filed(): JsonObject? {
    if (!Files.isRegularFile(path)) return null
    return try {
      Json.parseToJsonElement(Files.readAllBytes(path).decodeToString()).jsonObject
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  fun write(keys: Map<Uuid, ByteArray>) {
    val filed = JsonObject(
      keys.entries.associate { (id, key) -> id.toString() to JsonPrimitive(Base64.getEncoder().encodeToString(key)) },
    )
    path.parent.createDirectoriesDurably()
    path.writeAtomically { it.write(filed.toString().encodeToByteArray()) }
  }

  private fun entry(id: String, key: JsonElement): Pair<Uuid, ByteArray>? {
    val encoded = (key as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    return try {
      Uuid.parse(id) to Base64.getDecoder().decode(encoded)
    } catch (_: IllegalArgumentException) {
      null
    }
  }
}
