package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.connection.ConnectionLibrary
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.files.CorpusFile
import dev.marzahn.gloriana.logic.files.MemoryDestination
import dev.marzahn.gloriana.logic.presets.file.PresetJsonReader
import dev.marzahn.gloriana.logic.presets.storage.DefaultPreset
import dev.marzahn.gloriana.logic.presets.storage.PresetWrites
import dev.marzahn.gloriana.logic.storage.ChatsUsing
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.UuidV7IdFactory
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class PresetLibrary(
  directory: Path,
  database: GlorianaDatabase = desktopGlorianaDatabase(directory),
  faults: Faults = Faults.None,
  chats: ChatsUsing = ChatsUsing.NoneYet,
) : AutoCloseable {
  val connections = ConnectionLibrary(directory, database = database)
  private val updatedAt = UpdatedAtClock(connections.clock)
  private val writes =
    PresetWrites(database, database.presetDao(), UuidV7IdFactory, updatedAt, Tombstones(database, updatedAt), faults)
  private val reader = PresetJsonReader(UuidV7IdFactory)
  internal val default = DefaultPreset(database, connections.settings, database.presetDao(), writes, reader)
  val repository = PresetRepository(database.presetDao(), writes, UuidV7IdFactory, chats, default)
  val import = PresetImport(writes, reader)
  val global = GlobalModelSettings(connections.settings)
  val offer = SamplerOffer(connections.repository, global)

  suspend fun imported(corpusName: String): PresetImportResult.Imported =
    assertIs<PresetImportResult.Imported>(import.import(PresetCorpus.file(corpusName)))

  suspend fun stored(id: Uuid): Preset = assertIs<StoredPreset.Readable>(repository.preset(id).first()).preset

  suspend fun exported(id: Uuid): JsonObject {
    val destination = MemoryDestination()
    assertEquals(PresetExportResult.Exported, repository.export(id, destination))
    return Json.parseToJsonElement(destination.bytes.decodeToString()).jsonObject
  }

  override fun close() = connections.close()
}

object PresetCorpus {
  val presets = listOf(
    "sillytavern-1.19.1.json",
    "sillytavern-full.json",
    "gloriana-groups.json",
    "minimal.json",
    "only-prompts.json",
    "odd-order.json",
    "gloriana-not-object.json",
  )

  val keysNeverStored = setOf(
    "temperature", "top_p", "top_k", "frequency_penalty", "presence_penalty", "seed", "top_a", "min_p",
    "repetition_penalty", "openai_max_context", "openai_max_tokens", "reverse_proxy", "proxy_password",
    "custom_url", "custom_include_body", "custom_exclude_body", "custom_include_headers", "vertexai_region",
    "vertexai_express_project_id", "azure_base_url", "azure_deployment_name", "workers_ai_account_id",
  )

  fun bytes(name: String): ByteArray =
    checkNotNull(PresetCorpus::class.java.getResourceAsStream("/presets/$name")) { name }.use { it.readBytes() }

  fun file(name: String) = CorpusFile(name, bytes(name))

  fun json(name: String): JsonObject = Json.parseToJsonElement(bytes(name).decodeToString()).jsonObject

  fun storedPart(name: String) = JsonObject(json(name) - keysNeverStored)
}
