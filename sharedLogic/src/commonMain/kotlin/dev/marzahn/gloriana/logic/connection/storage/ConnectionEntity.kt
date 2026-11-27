package dev.marzahn.gloriana.logic.connection.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.CustomHeader
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.nameKey
import dev.marzahn.gloriana.logic.keys.SealedSecret
import dev.marzahn.gloriana.logic.storage.EntityType
import dev.marzahn.gloriana.logic.storage.StoredJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

internal const val CONNECTIONS_TABLE = "connections"

internal val ConnectionsEntityType = EntityType(CONNECTIONS_TABLE)

@Entity(tableName = CONNECTIONS_TABLE, indices = [Index(value = ["nameKey"], unique = true)])
internal data class ConnectionEntity(
  @PrimaryKey val id: Uuid,
  val name: String,
  val nameKey: String,
  val format: String,
  val baseUrl: String,
  val model: String,
  val contextSize: Int,
  val maxReplyTokens: Int,
  val streaming: Boolean,
  val includeUsage: Boolean,
  val timeoutSeconds: Int,
  val samplers: String,
  val customBody: String,
  val headers: String,
  val apiKey: String?,
  val updatedAt: Long,
)

@Serializable
private class StoredHeader(val name: String, val sealedValue: String)

internal sealed interface StoredConnection {
  class Readable(val connection: Connection) : StoredConnection

  data object Unreadable : StoredConnection
}

internal fun ConnectionEntity.decoded(): StoredConnection = try {
  ApiFormat.entries.firstOrNull { it.name == format }?.let { apiFormat ->
    StoredConnection.Readable(readable(apiFormat))
  } ?: StoredConnection.Unreadable
} catch (_: SerializationException) {
  StoredConnection.Unreadable
} catch (_: IllegalArgumentException) {
  StoredConnection.Unreadable
}

internal fun ConnectionEntity.readableOrNull(): Connection? = when (val stored = decoded()) {
  is StoredConnection.Readable -> stored.connection
  StoredConnection.Unreadable -> null
}

private fun ConnectionEntity.readable(apiFormat: ApiFormat) = Connection(
  id = id,
  fields = ConnectionFields(
    name = name,
    format = apiFormat,
    baseUrl = baseUrl,
    model = model,
    contextSize = contextSize,
    maxReplyTokens = maxReplyTokens,
    streaming = streaming,
    includeUsage = includeUsage,
    samplers = StoredJson.decodeFromString<Samplers>(samplers),
    timeoutSeconds = timeoutSeconds,
    customBody = StoredJson.decodeFromString<JsonObject>(customBody),
  ),
  key = apiKey?.let(::sealedSecret),
  headers = StoredJson.decodeFromString<List<StoredHeader>>(headers)
    .map { CustomHeader(it.name, sealedSecret(it.sealedValue)) },
)

internal fun Connection.toEntity(updatedAt: Long) = ConnectionEntity(
  id = id,
  name = fields.name,
  nameKey = nameKey(fields.name),
  format = fields.format.name,
  baseUrl = fields.baseUrl,
  model = fields.model,
  contextSize = fields.contextSize,
  maxReplyTokens = fields.maxReplyTokens,
  streaming = fields.streaming,
  includeUsage = fields.includeUsage,
  timeoutSeconds = fields.timeoutSeconds,
  samplers = StoredJson.encodeToString(fields.samplers),
  customBody = StoredJson.encodeToString(fields.customBody),
  headers = StoredJson.encodeToString(headers.map { StoredHeader(it.name, it.value.base64()) }),
  apiKey = key?.base64(),
  updatedAt = updatedAt,
)

private fun SealedSecret.base64(): String = Base64.encode(bytes)

private fun sealedSecret(base64: String) = SealedSecret(Base64.decode(base64))
