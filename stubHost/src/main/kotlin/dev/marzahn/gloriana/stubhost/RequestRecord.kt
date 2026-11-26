package dev.marzahn.gloriana.stubhost

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.Clock
import java.time.Instant

@Serializable
internal data class RecordedRequest(
  @Serializable(with = InstantText::class) val receivedAt: Instant,
  val method: String,
  val path: String,
  val headers: Map<String, List<String>>,
  val body: JsonElement,
)

internal class RequestRecord private constructor(
  val file: Path,
  private val clock: Clock,
  private val io: CoroutineDispatcher,
) {
  private val lock = Mutex()

  suspend fun append(method: String, path: String, headers: Map<String, List<String>>, body: String) {
    val request = RecordedRequest(clock.instant(), method, path, headers.mapKeys { it.key.lowercase() }, bodyJson(body))
    val line = Json.encodeToString(RecordedRequest.serializer(), request) + "\n"
    lock.withLock { withContext(io) { Files.writeString(file, line, APPEND) } }
  }

  suspend fun read(): String = lock.withLock { withContext(io) { Files.readString(file) } }

  companion object {
    fun open(file: Path, clock: Clock = Clock.systemUTC(), io: CoroutineDispatcher = Dispatchers.IO): RequestRecord {
      Files.newOutputStream(file, CREATE, APPEND).close()
      return RequestRecord(file, clock, io)
    }

    private fun bodyJson(body: String): JsonElement = when {
      body.isEmpty() -> JsonNull
      else -> runCatching { Json.parseToJsonElement(body) }.getOrElse { JsonPrimitive(body) }
    }
  }
}

private object InstantText : KSerializer<Instant> {
  override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

  override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
