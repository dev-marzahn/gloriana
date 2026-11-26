package dev.marzahn.gloriana.stubhost

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
internal val wireJson = Json {
  encodeDefaults = true
  explicitNulls = true
  ignoreUnknownKeys = true
  namingStrategy = JsonNamingStrategy.SnakeCase
}

@Serializable
internal data class ModelRequest(
  val model: String,
  @Serializable(with = UnquotedBoolean::class) val stream: Boolean? = null,
  val streamOptions: StreamOptions? = null,
) {
  val streams: Boolean get() = stream == true
  val includesUsage: Boolean get() = streamOptions?.includeUsage == true
}

@Serializable
internal data class StreamOptions(@Serializable(with = UnquotedBoolean::class) val includeUsage: Boolean? = null)

private object UnquotedBoolean : KSerializer<Boolean> {
  override val descriptor = PrimitiveSerialDescriptor("UnquotedBoolean", PrimitiveKind.BOOLEAN)

  override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)

  override fun deserialize(decoder: Decoder): Boolean {
    val element = decoder.decodeSerializableValue(JsonPrimitive.serializer())
    return element.takeUnless { it.isString }?.booleanOrNull ?: throw SerializationException("Expected true or false.")
  }
}

internal data class SseEvent(val name: String?, val data: String) {
  fun encoded(): String = buildString {
    name?.let { append("event: ").append(it).append('\n') }
    append("data: ").append(data).append("\n\n")
  }

  companion object {
    fun <T> unnamed(serializer: KSerializer<T>, value: T) = SseEvent(null, wireJson.encodeToString(serializer, value))

    fun <T> namedByType(serializer: KSerializer<T>, value: T): SseEvent =
      named(wireJson.encodeToJsonElement(serializer, value).jsonObject)

    fun named(json: JsonObject) = SseEvent(json.getValue("type").jsonPrimitive.content, json.toString())
  }
}
