package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.keys.HeaderName
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.OpenResult
import dev.marzahn.gloriana.logic.keys.SealedSecret
import dev.marzahn.gloriana.logic.keys.SecretSlot
import dev.marzahn.gloriana.stubhost.RunningStubHost
import dev.marzahn.gloriana.stubhost.startStubHost
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val TEST_KEY = "sk-stub/MARKER-1"
internal const val PLAIN = "Hello from the stub host. This reply arrives in parts."
internal val STUB_MODEL_IDS = listOf("stub-model-large", "stub-model-small")

internal val greeting = listOf(
  PromptSection(SectionRole.System, "You are Wren."),
  PromptSection(SectionRole.User, "Hello"),
)

internal fun header(name: String, value: String) = HostHeader(HeaderName.of(name), value)

internal fun openedConnection(
  format: ApiFormat,
  baseUrl: String = "https://host.example/v1",
  key: String? = TEST_KEY,
  headers: List<HostHeader> = emptyList(),
  fields: (ConnectionFields) -> ConnectionFields = { it },
) = OpenedConnection(fields(ConnectionFields("Stub", format, baseUrl, "model-1")), key, headers)

internal fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

internal class AnsweringVault(vault: KeyVault, private val answer: OpenResult) : KeyVault by vault {
  override suspend fun open(slot: SecretSlot, secret: SealedSecret): OpenResult = answer
}

internal class ThrowingVault(vault: KeyVault) : KeyVault by vault {
  override suspend fun open(slot: SecretSlot, secret: SealedSecret): OpenResult = error("a bug in the vault")
}

internal class StubHostReplies(directory: Path, delay: Duration = 3.seconds) : AutoCloseable {
  val host: RunningStubHost = runBlocking { startStubHost(directory.resolve("record.jsonl"), delay = delay) }
  val calls = HostCalls(OkHttpModelClients())
  val replies = ModelReplies(calls)

  fun connection(
    format: ApiFormat,
    mode: String,
    key: String? = TEST_KEY,
    headers: List<HostHeader> = emptyList(),
    fields: (ConnectionFields) -> ConnectionFields = { it },
  ) = openedConnection(format, host.baseUrl(mode), key, headers, fields)

  fun events(connection: OpenedConnection, global: Samplers = Samplers()): List<ModelEvent> =
    runBlocking { replies.reply(greeting, connection, global).toList() }

  fun recordedRequests(): List<JsonObject> = runBlocking { host.recorded() }
    .lines().filter(String::isNotBlank).map(::json)

  override fun close() = host.close()
}

internal fun JsonObject.recordedHeader(name: String): String? =
  getValue("headers").jsonObject[name]?.jsonArray?.single()?.jsonPrimitive?.content

internal fun List<ModelEvent>.text(): String = filterIsInstance<ModelEvent.TextDelta>().joinToString("") { it.text }

internal fun List<ModelEvent>.reasoning(): String =
  filterIsInstance<ModelEvent.ReasoningDelta>().joinToString("") { it.text }

internal fun List<ModelEvent>.failure(): ModelApiError? = (lastOrNull() as? ModelEvent.Failed)?.error

internal val everyWay: List<Pair<ApiFormat, Boolean>> =
  ApiFormat.entries.flatMap { format -> listOf(format to true, format to false) }

internal fun streaming(on: Boolean): (ConnectionFields) -> ConnectionFields = { it.copy(streaming = on) }
