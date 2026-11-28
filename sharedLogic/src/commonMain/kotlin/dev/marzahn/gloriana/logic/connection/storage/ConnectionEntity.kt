package dev.marzahn.gloriana.logic.connection.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.CustomHeader
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.holdsSealedValues
import dev.marzahn.gloriana.logic.json.decodedOrNull
import dev.marzahn.gloriana.logic.keys.SealedSecret
import dev.marzahn.gloriana.logic.storage.EntityType
import dev.marzahn.gloriana.logic.storage.StoredJson
import dev.marzahn.gloriana.logic.storage.nameKey
import dev.marzahn.gloriana.logic.text.parsedOrNull
import kotlinx.serialization.Serializable
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
  val position: Long,
  val updatedAt: Long,
)

@Serializable
private class StoredHeader(val name: String, val sealedValue: String)

internal sealed interface StoredConnection {
  class Readable(val connection: Connection) : StoredConnection

  data object Unreadable : StoredConnection
}

internal fun ConnectionEntity.decoded(): StoredConnection =
  readable()?.let(StoredConnection::Readable) ?: StoredConnection.Unreadable

internal fun ConnectionEntity.readableOrNull(): Connection? = when (val stored = decoded()) {
  is StoredConnection.Readable -> stored.connection
  StoredConnection.Unreadable -> null
}

internal data class SecretColumns(val id: Uuid, val format: String, val apiKey: String?, val headers: String)

internal val SecretColumns.needsDataKey: Boolean
  get() = decodedSecrets(format, apiKey, headers)?.let { holdsSealedValues(it.key, it.headers) } ?: true

private class DecodedSecrets(val format: ApiFormat, val key: SealedSecret?, val headers: List<CustomHeader>)

private fun decodedSecrets(format: String, apiKey: String?, headers: String): DecodedSecrets? {
  val apiFormat = ApiFormat.entries.firstOrNull { it.name == format }
  val key = apiKey?.let(::sealedSecret)
  val storedHeaders = customHeaders(headers)
  val keyReadable = apiKey == null || key != null
  return if (apiFormat != null && keyReadable && storedHeaders != null) {
    DecodedSecrets(apiFormat, key, storedHeaders)
  } else {
    null
  }
}

private fun customHeaders(headers: String): List<CustomHeader>? =
  StoredJson.decodedOrNull<List<StoredHeader>>(headers)?.allOrNull { stored ->
    sealedSecret(stored.sealedValue)?.let { CustomHeader(stored.name, it) }
  }

private fun <T, R : Any> List<T>.allOrNull(transform: (T) -> R?): List<R>? =
  mapNotNull(transform).takeIf { it.size == size }

private fun ConnectionEntity.readable(): Connection? {
  val secrets = decodedSecrets(format, apiKey, headers)
  val storedSamplers = StoredJson.decodedOrNull<Samplers>(samplers)
  val storedBody = StoredJson.decodedOrNull<JsonObject>(customBody)
  if (secrets == null || storedSamplers == null || storedBody == null) return null
  return Connection(
    id = id,
    fields = ConnectionFields(
      name = name,
      format = secrets.format,
      baseUrl = baseUrl,
      model = model,
      contextSize = contextSize,
      maxReplyTokens = maxReplyTokens,
      streaming = streaming,
      includeUsage = includeUsage,
      samplers = storedSamplers,
      timeoutSeconds = timeoutSeconds,
      customBody = storedBody,
    ),
    key = secrets.key,
    headers = secrets.headers,
  )
}

internal fun Connection.toEntity(position: Long, updatedAt: Long) = ConnectionEntity(
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
  position = position,
  updatedAt = updatedAt,
)

private fun SealedSecret.base64(): String = Base64.encode(bytes)

private fun sealedSecret(base64: String): SealedSecret? = parsedOrNull { Base64.decode(base64) }?.let(::SealedSecret)
